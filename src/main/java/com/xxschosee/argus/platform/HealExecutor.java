package com.xxschosee.argus.platform;

import com.xxschosee.argus.core.HealAction;

public interface HealExecutor {

    boolean apply(HealAction action);

    String name();
}
