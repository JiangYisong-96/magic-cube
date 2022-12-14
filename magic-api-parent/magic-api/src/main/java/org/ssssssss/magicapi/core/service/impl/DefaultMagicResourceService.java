package org.ssssssss.magicapi.core.service.impl;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.ssssssss.magicapi.core.config.Constants;
import org.ssssssss.magicapi.core.config.JsonCodeConstants;
import org.ssssssss.magicapi.core.resource.Resource;
import org.ssssssss.magicapi.core.resource.ZipResource;
import org.ssssssss.magicapi.core.event.EventAction;
import org.ssssssss.magicapi.core.event.FileEvent;
import org.ssssssss.magicapi.core.event.GroupEvent;
import org.ssssssss.magicapi.core.event.MagicEvent;
import org.ssssssss.magicapi.core.exception.InvalidArgumentException;
import org.ssssssss.magicapi.core.model.*;
import org.ssssssss.magicapi.core.service.AbstractPathMagicResourceStorage;
import org.ssssssss.magicapi.core.service.MagicResourceService;
import org.ssssssss.magicapi.core.service.MagicResourceStorage;
import org.ssssssss.magicapi.utils.IoUtils;
import org.ssssssss.magicapi.utils.JsonUtils;
import org.ssssssss.magicapi.utils.WebUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class DefaultMagicResourceService implements MagicResourceService, JsonCodeConstants, ApplicationListener<ApplicationStartedEvent> {

	private final Resource root;

	private final Map<String, Resource> groupMappings = new HashMap<>(16);

	private final Map<String, Group> groupCache = new HashMap<>(16);

	private final Map<String, Resource> fileMappings = new HashMap<>(32);

	private final Map<String, MagicEntity> fileCache = new HashMap<>(32);

	private final Map<String, Map<String, String>> pathCache = new HashMap<>(16);

	private final Map<String, MagicResourceStorage<? extends MagicEntity>> storages;

	private final ReadWriteLock lock = new ReentrantReadWriteLock();

	private final ApplicationEventPublisher publisher;

	private final Logger logger = LoggerFactory.getLogger(DefaultMagicResourceService.class);

	public DefaultMagicResourceService(Resource resource, List<MagicResourceStorage<? extends MagicEntity>> storages, ApplicationEventPublisher publisher) {
		this.root = resource;
		this.storages = storages.stream()
				.peek(it -> it.setMagicResourceService(this))
				.collect(Collectors.toMap(MagicResourceStorage::folder, it -> it));
		this.publisher = publisher;
	}

	public boolean processNotify(MagicNotify notify) {
		if (Constants.EVENT_TYPE_FILE.equals(notify.getType())) {
			return processFileNotify(notify.getId(), notify.getAction());
		}
		if (notify.getAction() == EventAction.CLEAR) {
			this.read(false);
			return true;
		}
		return processGroupNotify(notify.getId(), notify.getAction());
	}

	private boolean processGroupNotify(String id, EventAction action) {
		Group group = groupCache.get(id);
		if (group == null) {
			// create
			this.readAll();
			group = groupCache.get(id);
		}
		TreeNode<Group> treeNode = tree(group.getType()).findTreeNode(it -> it.getId().equals(id));
		if (treeNode != null) {
			GroupEvent event = new GroupEvent(group.getType(), action, group);
			event.setSource(Constants.EVENT_SOURCE_NOTIFY);
			if (event.getAction() == EventAction.DELETE) {
				event.setEntities(deleteGroup(id));
			} else if (action != EventAction.CREATE) {
				Resource folder = groupMappings.get(id);
				folder.readAll();
				if (folder.exists()) {
					// ??????????????????
					refreshGroup(folder, storages.get(group.getType()));
				} else {
					this.readAll();
					treeNode = tree(group.getType()).findTreeNode(it -> it.getId().equals(id));
				}
				event.setGroup(groupCache.get(id));
				event.setEntities(treeNode
						.flat()
						.stream()
						.flatMap(g -> listFiles(g.getId()).stream())
						.collect(Collectors.toList()));
			}
			publisher.publishEvent(event);
			return true;
		}
		return false;
	}

	private boolean processFileNotify(String id, EventAction action) {
		MagicEntity entity = fileCache.get(id);
		if (entity == null) {    // create
			this.readAll();
			entity = fileCache.get(id);
		}
		if (entity != null) {
			Group group = groupCache.get(entity.getGroupId());
			if (group != null) {
				MagicResourceStorage<? extends MagicEntity> storage = storages.get(group.getType());
				Map<String, String> pathCacheMap = storage.requirePath() ? pathCache.get(storage.folder()) : null;
				if (action == EventAction.DELETE) {
					fileMappings.remove(id);
					entity = fileCache.remove(id);
					if (pathCacheMap != null) {
						pathCacheMap.remove(id);
					}
				} else {
					Resource resource = fileMappings.get(id);
					resource.readAll();
					if (resource.exists()) {
						entity = storage.read(resource.read());
						putFile(storage, entity, resource);
					} else {
						this.readAll();
						entity = fileCache.get(id);
					}
				}
				publisher.publishEvent(new FileEvent(group.getType(), action, entity, Constants.EVENT_SOURCE_NOTIFY));
			}
		}
		return false;
	}

	private void init() {
		groupMappings.clear();
		groupCache.clear();
		fileMappings.clear();
		fileCache.clear();
		pathCache.clear();
		storages.forEach((key, registry) -> {
			if (registry.requirePath()) {
				pathCache.put(registry.folder(), new HashMap<>(32));
			}
			Resource folder = root.getDirectory(key);
			if (registry.allowRoot()) {
				String rootId = key + ":0";
				Group group = new Group();
				group.setId(rootId);
				group.setType(key);
				group.setParentId("0");
				putGroup(group, folder);
			}
			if (!folder.exists()) {
				folder.mkdir();
			}
		});
	}

	private void read(boolean triggerEvent) {
		writeLock(() -> {
			if (triggerEvent) {
				publisher.publishEvent(new MagicEvent("clear", EventAction.CLEAR));
			}
			this.readAll();
			fileCache.values().forEach(entity -> {
				Group group = groupCache.get(entity.getGroupId());
				publisher.publishEvent(new FileEvent(group.getType(), EventAction.LOAD, entity));
			});
			return null;
		});
	}

	private void readAll() {
		writeLock(() -> {
			this.init();
			this.root.readAll();
			storages.forEach((key, registry) -> refreshGroup(root.getDirectory(key), registry));
			return null;
		});
	}

	@Override
	public void refresh() {
		this.read(true);
	}

	@Override
	public Resource getResource() {
		return root;
	}

	private void refreshGroup(Resource folder, MagicResourceStorage<? extends MagicEntity> storage) {
		if (storage.allowRoot()) {
			folder.files(storage.suffix()).forEach(file -> putFile(storage, storage.readResource(file), file));
		} else {
			folder.dirs().forEach(dir -> {
				Resource meta = dir.getResource(Constants.GROUP_METABASE);
				if (meta.exists()) {
					putGroup(Objects.requireNonNull(JsonUtils.readValue(meta.read(), Group.class)), dir);
					dir.files(storage.suffix()).forEach(file -> putFile(storage, storage.readResource(file), file));
				}
			});
		}
	}

	@Override
	public boolean saveGroup(Group group) {
		isTrue(!root.readonly(), IS_READ_ONLY);
		// ????????????
		isTrue(storages.containsKey(group.getType()), NOT_SUPPORTED_GROUP_TYPE);
		// ????????????
		notNull(group.getName(), NAME_REQUIRED);
		notNull(IoUtils.validateFileName(group.getName()), NAME_INVALID);
		// ????????????parentId
		notNull(group.getParentId(), GROUP_ID_REQUIRED);
		MagicResourceStorage<? extends MagicEntity> storage = storages.get(group.getType());
		return writeLock(() -> {
			Resource resource;
			// ????????????????????????????????????
			if (Constants.ROOT_ID.equals(group.getParentId())) {
				resource = root.getDirectory(group.getType());
			} else {
				// ??????????????????
				resource = getGroupResource(group.getParentId());
				// ????????????????????????
				isTrue(resource != null && resource.exists(), GROUP_NOT_FOUND);
			}
			Resource groupResource;
			GroupEvent event = new GroupEvent(group.getType(), group.getId() == null ? EventAction.CREATE : EventAction.SAVE, group);
			if (group.getId() == null || !groupCache.containsKey(group.getId())) {
				// ????????????
				if (group.getId() == null) {
					group.setId(UUID.randomUUID().toString().replace("-", ""));
				}
				group.setCreateTime(System.currentTimeMillis());
				group.setCreateBy(WebUtils.currentUserName());
				groupResource = resource.getDirectory(group.getName());
				// ????????????????????????????????????
				isTrue(!groupResource.exists(), FILE_SAVE_FAILURE);
				// ???????????????
				groupResource.mkdir();
			} else {
				Group oldGroup = groupCache.get(group.getId());
				if (storage.requirePath() && !Objects.equals(oldGroup.getPath(), group.getPath())) {
					TreeNode<Group> treeNode = tree(group.getType());
					String oldPath = oldGroup.getPath();
					oldGroup.setPath(group.getPath());
					// ??????????????????????????????
					List<MagicEntity> entities = treeNode.findTreeNode(it -> it.getId().equals(group.getId()))
							.flat()
							.stream()
							.flatMap(it -> fileCache.values().stream().filter(f -> f.getGroupId().equals(it.getId())))
							.collect(Collectors.toList());
					for (MagicEntity entity : entities) {
						String newMappingKey = storage.buildKey(entity);
						if (pathCache.get(group.getType()).entrySet().stream().anyMatch(entry -> entry.getValue().equals(newMappingKey) && !entry.getKey().equals(entity.getId()))) {
							// ??????path
							oldGroup.setPath(oldPath);
							throw new InvalidArgumentException(SAVE_GROUP_PATH_CONFLICT);
						}
					}
				}
				Resource oldResource = getGroupResource(group.getId());
				groupResource = resource.getDirectory(group.getName());
				isTrue(oldResource != null && oldResource.exists(), GROUP_NOT_FOUND);
				// ??????????????????????????????
				group.setUpdateBy(WebUtils.currentUserName());
				group.setUpdateTime(System.currentTimeMillis());
				// ???????????????????????????
				if (!Objects.equals(oldGroup.getName(), group.getName())) {
					// ????????????????????????????????????
					isTrue(!groupResource.exists(), FILE_SAVE_FAILURE);
					isTrue(oldResource.renameTo(groupResource), FILE_SAVE_FAILURE);
				}
			}
			// ??????????????????
			if (groupResource.getResource(Constants.GROUP_METABASE).write(JsonUtils.toJsonString(group))) {
				putGroup(group, groupResource);
				TreeNode<Group> treeNode = tree(group.getType()).findTreeNode(it -> it.getId().equals(group.getId()));
				// ??????????????????
				refreshGroup(groupResource, storage);
				if (event.getAction() != EventAction.CREATE) {
					event.setEntities(treeNode
							.flat()
							.stream()
							.flatMap(g -> listFiles(g.getId()).stream())
							.collect(Collectors.toList()));
				}
				publisher.publishEvent(event);
				return true;
			}
			return false;
		});
	}


	@Override
	public boolean move(String src, String groupId) {
		isTrue(!root.readonly(), IS_READ_ONLY);
		Group group = groupCache.get(groupId);
		isTrue("0".equals(groupId) || group != null, GROUP_NOT_FOUND);
		isTrue(!Objects.equals(src, groupId), MOVE_NAME_CONFLICT);
		return writeLock(() -> {
			Group srcGroup = groupCache.get(src);
			if (srcGroup != null) {
				// ????????????
				return moveGroup(srcGroup, groupId);
			} else {
				// ????????????????????????????????????
				notNull(group, GROUP_NOT_FOUND);
				MagicEntity entity = fileCache.get(src);
				notNull(entity, FILE_NOT_FOUND);
				// ????????????
				return moveFile(entity.copy(), group);
			}
		});
	}

	@Override
	public String copyGroup(String src, String groupId) {
		isTrue(!root.readonly(), IS_READ_ONLY);
		Group group = groupCache.get(groupId);
		isTrue("0".equals(groupId) || group != null, GROUP_NOT_FOUND);
		isTrue(!Objects.equals(src, groupId), SRC_GROUP_CONFLICT);
		Group srcGroup = groupCache.get(src);
		notNull(srcGroup, GROUP_NOT_FOUND);
		Group newGroup = new Group();
		newGroup.setType(srcGroup.getType());
		newGroup.setParentId(groupId);
		newGroup.setName(srcGroup.getName() + "(Copy)");
		newGroup.setPath(Objects.toString(srcGroup.getPath(), "") + "_copy");
		newGroup.setOptions(srcGroup.getOptions());
		newGroup.setPaths(srcGroup.getPaths());
		newGroup.setProperties(srcGroup.getProperties());
		saveGroup(newGroup);
		listFiles(src).stream()
				.map(MagicEntity::copy)
				.peek(it -> it.setGroupId(newGroup.getId()))
				.peek(it -> it.setId(null))
				.forEach(this::saveFile);
		return newGroup.getId();
	}

	/**
	 * ????????????
	 *
	 * @param src    ????????????
	 * @param target ????????????ID
	 */
	private boolean moveGroup(Group src, String target) {
		isTrue(!root.readonly(), IS_READ_ONLY);
		MagicResourceStorage<?> storage = storages.get(src.getType());
		Resource targetResource = Constants.ROOT_ID.equals(target) ? this.root.getDirectory(storage.folder()) : groupMappings.get(target);
		// ?????????????????????????????????
		isTrue(!targetResource.getDirectory(src.getName()).exists(), MOVE_NAME_CONFLICT);
		targetResource = targetResource.getDirectory(src.getName());
		TreeNode<Group> treeNode = tree(storage.folder());
		String oldParentId = src.getParentId();
		src.setParentId(target);
		// ??????????????????????????????
		List<MagicEntity> entities = treeNode.findTreeNode(it -> it.getId().equals(src.getId()))
				.flat()
				.stream()
				.flatMap(it -> fileCache.values().stream().filter(f -> f.getGroupId().equals(it.getId())))
				.collect(Collectors.toList());
		if (storage.requirePath()) {
			for (MagicEntity entity : entities) {
				String newMappingKey = storage.buildKey(entity);
				if (pathCache.get(src.getType()).entrySet().stream().anyMatch(entry -> entry.getValue().equals(newMappingKey) && !entry.getKey().equals(entity.getId()))) {
					// ??????parentId
					src.setParentId(oldParentId);
					throw new InvalidArgumentException(MOVE_PATH_CONFLICT);
				}
			}
		}
		// ??????????????????????????????
		src.setUpdateBy(WebUtils.currentUserName());
		src.setUpdateTime(System.currentTimeMillis());
		Resource oldResource = groupMappings.get(src.getId());
		if (oldResource.renameTo(targetResource)) {
			Resource resource = targetResource.getResource(Constants.GROUP_METABASE);
			if (resource.write(JsonUtils.toJsonString(src))) {
				// ??????group??????
				putGroup(src, targetResource);
				// ??????mapping??????
				if (storage.requirePath()) {
					Map<String, String> selfPathCache = pathCache.get(storage.folder());
					entities.forEach(entity -> selfPathCache.put(entity.getId(), storage.buildKey(entity)));
				}
				// ????????????
				refreshGroup(targetResource, storage);
				publisher.publishEvent(new GroupEvent(src.getType(), EventAction.MOVE, src, entities));
				return true;
			}
		}
		return false;
	}

	/**
	 * ????????????
	 *
	 * @param entity ????????????
	 * @param group  ????????????
	 */
	private <T extends MagicEntity> boolean moveFile(T entity, Group group) {
		isTrue(!root.readonly(), IS_READ_ONLY);
		// ?????????????????????
		isTrue(!Constants.LOCK.equals(entity.getLock()), RESOURCE_LOCKED);
		// ??????????????????ID
		entity.setGroupId(group.getId());
		MagicResourceStorage<?> storage = storages.get(group.getType());
		// ??????mappingKey
		String newMappingKey = storage.buildKey(entity);
		Resource resource = groupMappings.get(group.getId());
		// ????????????????????????????????????
		Resource newResource = resource.getResource(entity.getName() + storage.suffix());
		isTrue(!newResource.exists(), MOVE_NAME_CONFLICT);
		isTrue(!storage.requirePath() || pathCache.get(storage.folder()).entrySet().stream().noneMatch(entry -> entry.getValue().equals(newMappingKey) && !entry.getKey().equals(entity.getId())), MOVE_PATH_CONFLICT);
		// ??????????????????????????????
		entity.setUpdateBy(WebUtils.currentUserName());
		entity.setUpdateTime(System.currentTimeMillis());
		// ???????????????
		if (newResource.write(storage.write(entity))) {
			// ???????????????
			fileMappings.remove(entity.getId()).delete();
			// ????????????
			putFile(storage, entity, newResource);
			publisher.publishEvent(new FileEvent(group.getType(), EventAction.MOVE, entity));
			return true;
		}
		return false;
	}

	@Override
	public TreeNode<Group> tree(String type) {
		return readLock(() -> groupCache.values().stream().filter(it -> type.equals(it.getType())).collect(Collectors.collectingAndThen(Collectors.toList(), this::convertToTree)));
	}

	@Override
	public Map<String, TreeNode<Group>> tree() {
		return readLock(() -> groupCache.values().stream().collect(Collectors.groupingBy(Group::getType, Collectors.collectingAndThen(Collectors.toList(), this::convertToTree))));
	}

	@Override
	public List<Group> getGroupsByFileId(String id) {
		return readLock(() -> {
			List<Group> groups = new ArrayList<>();
			MagicEntity entity = fileCache.get(id);
			if (entity != null) {
				Group group = groupCache.get(entity.getGroupId());
				while (group != null) {
					groups.add(group);
					group = groupCache.get(group.getParentId());
				}
			}
			return groups;
		});
	}

	private TreeNode<Group> convertToTree(List<Group> groups) {
		TreeNode<Group> root = new TreeNode<>();
		root.setNode(new Group("0", "root"));
		convertToTree(groups, root);
		return root;
	}

	private void convertToTree(List<Group> remains, TreeNode<Group> current) {
		Group temp;
		List<TreeNode<Group>> childNodes = new LinkedList<>();
		Iterator<Group> iterator = remains.iterator();
		while (iterator.hasNext()) {
			temp = iterator.next();
			if (current.getNode().getId().equals(temp.getParentId())) {
				childNodes.add(new TreeNode<>(temp));
				iterator.remove();
			}
		}
		current.setChildren(childNodes);
		childNodes.forEach(it -> convertToTree(remains, it));
	}


	@Override
	public Resource getGroupResource(String id) {
		return groupMappings.get(id);
	}

	@Override
	public <T extends MagicEntity> boolean saveFile(T entity) {
		isTrue(!root.readonly(), IS_READ_ONLY);
		// ??????????????????
		notNull(entity.getGroupId(), GROUP_ID_REQUIRED);
		// ????????????
		notBlank(entity.getName(), NAME_REQUIRED);
		isTrue(IoUtils.validateFileName(entity.getName()), NAME_INVALID);
		return writeLock(() -> {
			EventAction action = entity.getId() == null || !fileCache.containsKey(entity.getId()) ? EventAction.CREATE : EventAction.SAVE;
			// ??????????????????
			Resource groupResource = getGroupResource(entity.getGroupId());
			// ??????????????????
			notNull(groupResource, GROUP_NOT_FOUND);
			MagicResourceStorage<T> storage;
			if (entity.getGroupId().contains(":")) {
				storage = (MagicResourceStorage<T>) this.storages.get(entity.getGroupId().split(":")[0]);
			} else {
				// ??????????????????
				Group group = groupCache.get(entity.getGroupId());
				storage = (MagicResourceStorage<T>) this.storages.get(group.getType());
			}

			// ????????????
			if (storage.requiredScript()) {
				notBlank(entity.getScript(), SCRIPT_REQUIRED);
			}
			// ????????????
			if (storage.requirePath()) {
				notBlank(((PathMagicEntity) entity).getPath(), PATH_REQUIRED);
				String newMappingKey = storage.buildKey(entity);
				isTrue(pathCache.get(storage.folder()).entrySet().stream().noneMatch(entry -> entry.getValue().equals(newMappingKey) && !entry.getKey().equals(entity.getId())), PATH_CONFLICT);
			}
			storage.validate(entity);
			// ???????????????
			String filename = entity.getName() + storage.suffix();
			// ????????????????????????
			Resource fileResource = groupResource.getResource(filename);
			if (action == EventAction.CREATE) {
				if (entity.getId() == null) {
					isTrue(!fileResource.exists(), FILE_SAVE_FAILURE);
					// ??????????????????
					entity.setId(UUID.randomUUID().toString().replace("-", ""));
				}
				entity.setCreateTime(System.currentTimeMillis());
				entity.setCreateBy(WebUtils.currentUserName());
			} else {
				// ??????????????????
				entity.setUpdateTime(System.currentTimeMillis());
				entity.setUpdateBy(WebUtils.currentUserName());
				isTrue(!Constants.LOCK.equals(fileCache.get(entity.getId()).getLock()), RESOURCE_LOCKED);
				Resource oldFileResource = fileMappings.get(entity.getId());
				if (!oldFileResource.name().equals(fileResource.name())) {
					// ?????????
					isTrue(oldFileResource.renameTo(fileResource), FILE_SAVE_FAILURE);
				}
			}
			boolean flag = fileResource.write(storage.write(entity));
			if (flag) {
				publisher.publishEvent(new FileEvent(storage.folder(), action, entity));
				putFile(storage, entity, fileResource);
			}
			return flag;
		});
	}

	private List<MagicEntity> deleteGroup(String id) {
		isTrue(!root.readonly(), IS_READ_ONLY);
		Group group = groupCache.get(id);
		List<MagicEntity> entities = new ArrayList<>();
		// ???????????????????????????
		tree(group.getType())
				.findTreeNode(it -> it.getId().equals(id))
				.flat()
				.forEach(g -> {
					groupCache.remove(g.getId());
					groupMappings.remove(g.getId());
					fileCache.values().stream()
							.filter(f -> f.getGroupId().equals(g.getId())).peek(entities::add)
							.collect(Collectors.toList())
							.forEach(file -> {
								fileCache.remove(file.getId());
								fileMappings.remove(file.getId());
								Map<String, String> map = pathCache.get(g.getType());
								if (map != null) {
									map.remove(file.getId());
								}
							});
				});
		groupMappings.remove(id);
		groupCache.remove(id);
		return entities;
	}

	@Override
	public boolean delete(String id) {
		isTrue(!root.readonly(), IS_READ_ONLY);
		return writeLock(() -> {
			Resource resource = getGroupResource(id);
			if (resource != null) {
				// ????????????
				if (resource.exists() && resource.delete()) {
					Group group = groupCache.get(id);
					GroupEvent event = new GroupEvent(groupCache.get(group.getId()).getType(), EventAction.DELETE, group);
					event.setEntities(deleteGroup(id));
					publisher.publishEvent(event);
					return true;
				}
			}
			resource = fileMappings.get(id);
			// ????????????
			if (resource != null && resource.exists() && resource.delete()) {
				MagicEntity entity = fileCache.remove(id);
				String type = groupCache.get(entity.getGroupId()).getType();
				publisher.publishEvent(new FileEvent(type, EventAction.DELETE, entity));
				fileMappings.remove(id);
				fileCache.remove(id);
				Map<String, String> map = pathCache.get(type);
				if (map != null) {
					map.remove(id);
				}

			}
			return true;
		});
	}

	@Override
	public <T extends MagicEntity> List<T> listFiles(String groupId) {
		return readLock(() -> {
			Group group = groupCache.get(groupId);
			notNull(group, GROUP_NOT_FOUND);
			return fileCache.values().stream()
					.filter(it -> it.getGroupId().equals(groupId))
					.map(it -> (T) it)
					.collect(Collectors.toList());
		});
	}

	@Override
	public <T extends MagicEntity> List<T> files(String type) {
		MagicResourceStorage<? extends MagicEntity> storage = storages.get(type);
		Resource directory = root.getDirectory(type);
		if (directory.exists()) {
			return directory.files(storage.suffix()).stream()
					.map(storage::readResource)
					.map(it -> (T) it)
					.collect(Collectors.toList());
		}
		return Collections.emptyList();
	}

	@Override
	public <T extends MagicEntity> T file(String id) {
		return (T) fileCache.get(id);
	}

	@Override
	public Group getGroup(String id) {
		return groupCache.get(id);
	}

	@Override
	public void export(String groupId, List<SelectedResource> resources, OutputStream os) throws IOException {
		if (StringUtils.isNotBlank(groupId)) {
			Resource resource = getGroupResource(groupId);
			notNull(resource, GROUP_NOT_FOUND);
			resource.export(os);
		} else if (resources == null || resources.isEmpty()) {
			root.export(os);
		} else {
			ZipOutputStream zos = new ZipOutputStream(os);
			for (SelectedResource item : resources) {
				if ("root".equals(item.getType())) {
					zos.putNextEntry(new ZipEntry(item.getId() + "/"));
				} else if ("group".equals(item.getType())) {
					Resource resource = getGroupResource(item.getId());
					notNull(resource, GROUP_NOT_FOUND);
					zos.putNextEntry(new ZipEntry(resource.getFilePath()));
					zos.closeEntry();
					resource = resource.getResource(Constants.GROUP_METABASE);
					zos.putNextEntry(new ZipEntry(resource.getFilePath()));
					zos.write(resource.read());
					zos.closeEntry();
				} else {
					Resource resource = fileMappings.get(item.getId());
					MagicEntity entity = fileCache.get(item.getId());
					notNull(entity, FILE_NOT_FOUND);
					Resource groupResource = groupMappings.get(entity.getGroupId());
					Group group = groupCache.get(entity.getGroupId());
					MagicResourceStorage<? extends MagicEntity> storage = storages.get(group.getType());
					zos.putNextEntry(new ZipEntry(groupResource.getFilePath() + entity.getName() + storage.suffix()));
					zos.write(resource.read());
					zos.closeEntry();
				}
			}
			zos.flush();
			zos.close();
		}
	}

	@Override
	public boolean lock(String id) {
		return doLockResource(id, Constants.LOCK);
	}

	private boolean doLockResource(String id, String lockState) {
		isTrue(!root.readonly(), IS_READ_ONLY);
		return writeLock(() -> {
			MagicEntity entity = fileCache.get(id);
			Resource resource = fileMappings.get(id);
			notNull(entity, FILE_NOT_FOUND);
			notNull(resource, FILE_NOT_FOUND);
			Group group = groupCache.get(entity.getGroupId());
			notNull(group, GROUP_NOT_FOUND);
			entity.setLock(lockState);
			entity.setUpdateTime(System.currentTimeMillis());
			entity.setUpdateBy(WebUtils.currentUserName());
			MagicResourceStorage<? extends MagicEntity> storage = storages.get(group.getType());
			boolean flag = resource.write(storage.write(entity));
			if (flag) {
				putFile(storage, entity, resource);
			}
			return flag;
		});
	}

	@Override
	public boolean unlock(String id) {
		return doLockResource(id, Constants.UNLOCK);
	}

	@Override
	public boolean upload(InputStream inputStream, boolean full) throws IOException {
		isTrue(!root.readonly(), IS_READ_ONLY);
		try {
			ZipResource zipResource = new ZipResource(inputStream);
			Set<Group> groups = new LinkedHashSet<>();
			Set<MagicEntity> entities = new LinkedHashSet<>();
			return writeLock(() -> {
				readAllResource(zipResource, groups, entities, !full);
				if (full) {
					// ??????????????????????????????
					root.delete();
					this.init();
					publisher.publishEvent(new MagicEvent("clear", EventAction.CLEAR));
				}
				for (Group group : groups) {
					saveGroup(group);
				}
				for (MagicEntity entity : entities) {
					saveFile(entity);
				}
				return true;
			});
		} finally {
			IoUtils.close(inputStream);
		}

	}

	private void readAllResource(Resource root, Set<Group> groups, Set<MagicEntity> entities, boolean checked) {
		Resource resource = root.getResource(Constants.GROUP_METABASE);
		MagicResourceStorage<? extends MagicEntity> storage = null;
		if (resource.exists()) {
			Group group = JsonUtils.readValue(resource.read(), Group.class);
			group.setType(mappingV1Type(group.getType()));
			storage = storages.get(group.getType());
			notNull(storage, NOT_SUPPORTED_GROUP_TYPE);
		}
		readAllResource(root, storage, groups, entities, null, "/", checked);
	}

	private void readAllResource(Resource root, MagicResourceStorage<? extends MagicEntity> storage, Set<Group> groups, Set<MagicEntity> entities, Set<String> mappingKeys, String path, boolean checked) {
		storage = storage == null ? storages.get(root.name()) : storage;
		if (storage != null) {
			mappingKeys = mappingKeys == null ? new HashSet<>() : mappingKeys;
			if (!storage.allowRoot()) {
				Resource resource = root.getResource(Constants.GROUP_METABASE);
				// ????????????????????????????????????????????????
				if (resource.exists()) {
					// ??????????????????
					Group group = JsonUtils.readValue(resource.read(), Group.class);
					group.setType(mappingV1Type(group.getType()));
					groups.add(group);
					if (storage.requirePath()) {
						path = path + Objects.toString(group.getPath(), "") + "/";
					}
				}

			}
			for (Resource file : root.files(storage.suffix())) {
				MagicEntity entity = storage.read(file.read());
				if (storage.allowRoot()) {
					entity.setGroupId(storage.folder() + ":0");
				}
				String mappingKey;
				if (storage instanceof AbstractPathMagicResourceStorage) {
					mappingKey = ((AbstractPathMagicResourceStorage) storage).buildMappingKey((PathMagicEntity) entity, path);
				} else {
					mappingKey = storage.buildKey(entity);
				}
				if (checked) {
					String groupId = entity.getGroupId();
					// ???????????????????????????
					fileCache.values().stream()
							// ????????????
							.filter(it -> Objects.equals(it.getGroupId(), groupId))
							// ?????????
							.filter(it -> !it.getId().equals(entity.getId()))
							.forEach(it -> isTrue(!Objects.equals(it.getName(), entity.getName()), RESOURCE_PATH_CONFLICT.format(entity.getName())));

				}
				// ??????
				isTrue(mappingKeys.add(mappingKey), RESOURCE_PATH_CONFLICT.format(mappingKey));
				entities.add(entity);
			}

		}
		for (Resource directory : root.dirs()) {
			readAllResource(directory, storage, groups, entities, mappingKeys, path, checked);
		}
	}

	/**
	 * ??????1.x??????
	 */
	private String mappingV1Type(String type) {
		if ("1".equals(type)) {
			return "api";
		} else if ("2".equals(type)) {
			return "function";
		}
		return type;

	}

	@Override
	public String getGroupName(String groupId) {
		return findGroups(groupId).stream()
				.map(Group::getName)
				.collect(Collectors.joining("/"));
	}

	@Override
	public String getGroupPath(String groupId) {
		return findGroups(groupId).stream()
				.map(Group::getPath)
				.filter(StringUtils::isNotBlank)
				.collect(Collectors.joining("/"));
	}

	private List<Group> findGroups(String groupId) {
		return readLock(() -> {
			List<Group> groups = new ArrayList<>();
			String key = groupId;
			while (groupCache.containsKey(key)) {
				Group group = groupCache.get(key);
				groups.add(0, group);
				key = group.getParentId();
			}
			return groups;
		});
	}

	private void putGroup(Group group, Resource resource) {
		groupMappings.put(group.getId(), resource);
		groupCache.put(group.getId(), group);
	}

	private void putFile(MagicResourceStorage<?> storage, MagicEntity entity, Resource resource) {
		fileMappings.put(entity.getId(), resource);
		fileCache.put(entity.getId(), entity);
		if (storage.requirePath()) {
			pathCache.get(storage.folder()).put(entity.getId(), storage.buildKey(entity));
		}
	}

	private <R> R readLock(Supplier<R> supplier) {
		try {
			lock.readLock().lock();
			return supplier.get();
		} finally {
			lock.readLock().unlock();
		}
	}

	private <R> R writeLock(Supplier<R> supplier) {
		try {
			lock.writeLock().lock();
			return supplier.get();
		} finally {
			lock.writeLock().unlock();
		}
	}

	@Override
	public void onApplicationEvent(ApplicationStartedEvent applicationStartedEvent) {
		try {
			this.read(false);
		} catch (Exception e) {
			logger.error("???????????????????????????", e);
		}
	}
}
