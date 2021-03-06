/*
 * Copyright (c) 2014 The APN-PROXY Project
 *
 * The APN-PROXY Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.xx_dev.apn.proxy.config;

import org.apache.commons.lang.StringUtils;

/**
 * @author xmx
 * @version $Id: com.xx_dev.apn.proxy.config.ApnProxyListenType 14-1-8 16:13 (xmx) Exp $
 */
public enum ApnProxyListenType {
    SSL, AES, PLAIN;

    public static ApnProxyListenType fromString(String _listenType) {
        if (StringUtils.equals(_listenType, "ssl")) {
            return ApnProxyListenType.SSL;
        } else if (StringUtils.equals(_listenType, "aes")) {
            return ApnProxyListenType.AES;
        } else if (StringUtils.equals(_listenType, "plain")) {
            return ApnProxyListenType.PLAIN;
        } else {
            throw new ApnProxyConfigException("Unknown listen type");
        }
    }
}
