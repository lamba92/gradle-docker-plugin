/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bmuschko.gradle.docker.tasks

import com.github.dockerjava.api.model.Info
import groovy.transform.CompileStatic

@CompileStatic
class DockerInfo extends AbstractDockerRemoteApiTask {

    @Override
    void runRemoteCommand() {
        logger.quiet "Retrieving Docker info."
        Info info = dockerClient.infoCmd().exec()

        if (nextHandler) {
            nextHandler.execute(info)
        } else {
            logger.quiet "Debug                : $info.debug"
            logger.quiet "Containers           : $info.containers"
            logger.quiet "Driver               : $info.driver"
            logger.quiet "Driver Statuses      : $info.driverStatuses"
            logger.quiet "Images               : $info.images"
            logger.quiet "IPv4 Forwarding      : $info.IPv4Forwarding"
            logger.quiet "Index Server Address : $info.indexServerAddress"
            logger.quiet "Init Path            : $info.initPath"
            logger.quiet "Init SHA1            : $info.initSha1"
            logger.quiet "Kernel Version       : $info.kernelVersion"
            logger.quiet "Sockets              : $info.sockets"
            logger.quiet "Memory Limit         : $info.memoryLimit"
            logger.quiet "nEvent Listener      : $info.NEventsListener"
            logger.quiet "NFd                  : $info.NFd"
            logger.quiet "NGoroutines          : $info.NGoroutines"
            logger.quiet "Swap Limit           : $info.swapLimit"
            logger.quiet "Execution Driver     : $info.executionDriver"
        }
    }
}
