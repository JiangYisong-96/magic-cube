package org.ssssssss.magicapi.modules;

import java.beans.Transient;
import org.ssssssss.script.MagicScriptContext;

public interface DynamicModule<T> {

  @Transient
  T getDynamicModule(MagicScriptContext context);
}
