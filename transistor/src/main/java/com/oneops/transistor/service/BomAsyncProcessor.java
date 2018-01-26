/*******************************************************************************
 *
 *   Copyright 2015 Walmart, Inc.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 *******************************************************************************/
package com.oneops.transistor.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.oneops.cms.cm.domain.CmsCI;
import com.oneops.cms.cm.service.CmsCmProcessor;
import com.oneops.cms.dj.domain.CmsDeployment;
import com.oneops.cms.dj.domain.CmsRelease;
import org.apache.log4j.Logger;

import com.oneops.cms.exceptions.CmsBaseException;
import com.oneops.cms.util.CmsError;
import com.oneops.transistor.exceptions.TransistorException;

public class BomAsyncProcessor {

    private static final String THREAD_PREFIX_BOM = "async-env-";
    private static final String THREAD_PREFIX_FLEX = "async-flex-";
    static Logger logger = Logger.getLogger(BomAsyncProcessor.class);

    private CmsCmProcessor cmProcessor;
    private BomManager bomManager;
    private FlexManager flexManager;
    private EnvSemaphore envSemaphore;
    private Gson gson = new Gson();

    public void setCmProcessor(CmsCmProcessor cmProcessor) {
        this.cmProcessor = cmProcessor;
    }

    public void setFlexManager(FlexManager flexManager) {
        this.flexManager = flexManager;
    }

    public void setBomManager(BomManager bomManager) {
        this.bomManager = bomManager;
    }

    public void setEnvSemaphore(EnvSemaphore envSemaphore) {
        this.envSemaphore = envSemaphore;
    }

    public void compileEnv(long envId, String userId, Set<Long> excludePlats, CmsDeployment dpmt, String desc, boolean commit) {
        final String processId = UUID.randomUUID().toString();
        envSemaphore.lockEnv(envId, EnvSemaphore.LOCKED_STATE, processId);

        Thread t = new Thread(() -> {
            String envMsg = null;
            try {
                long startTime = System.currentTimeMillis();
                CmsCI environment = cmProcessor.getCiById(envId);
                bomManager.check4openDeployment(environment.getNsPath() + "/" + environment.getCiName() + "/bom");
                CmsRelease bomRelease;
                boolean deploy =(dpmt != null);
                if (deploy) {
                    bomRelease = bomManager.generateAndDeployBom(envId, userId, excludePlats, dpmt, commit);
                }
                else {
                    bomRelease = bomManager.generateBom(envId, userId, excludePlats, desc, commit);
                }
                Map releaseInfo = gson.fromJson(bomRelease.getDescription(), HashMap.class);
                releaseInfo.put("createdBy", userId);
                releaseInfo.put("mode", "persistent");
                releaseInfo.put("autoDeploy", deploy);
                releaseInfo.put("releaseId", bomRelease.getReleaseId());
                envMsg = EnvSemaphore.SUCCESS_PREFIX + " Generation time taken: " + ((System.currentTimeMillis() - startTime) / 1000.0) + " seconds. releaseInfo=" + gson.toJson(releaseInfo);
            } catch (Exception e) {
                logger.error("Exception in build bom ", e);
                envMsg = EnvSemaphore.BOM_ERROR + e.getMessage();
                throw new TransistorException(CmsError.TRANSISTOR_BOM_GENERATION_FAILED, envMsg);
            } finally {
                envSemaphore.unlockEnv(envId, envMsg, processId);
            }
        }, getThreadName(THREAD_PREFIX_BOM, envId));
        t.start();
    }

    public void processFlex(long envId, long flexRelId, int step, boolean scaleUp) {
        final String processId = UUID.randomUUID().toString();
        envSemaphore.lockEnv(envId, EnvSemaphore.LOCKED_STATE, processId);
        Thread t = new Thread(() -> {
            String envMsg = null;
            try {
                flexManager.processFlex(flexRelId, step, scaleUp, envId);
                envMsg = "";
            } catch (CmsBaseException e) {
                logger.error("Exception occurred while flexing the ", e);
                envMsg = EnvSemaphore.BOM_ERROR + e.getMessage();
            } finally {
                envSemaphore.unlockEnv(envId, envMsg, processId);
            }
        }, getThreadName(THREAD_PREFIX_FLEX, envId));
        t.start();
    }

    public void resetEnv(long envId) {
        envSemaphore.resetEnv(envId);
    }

    private String getThreadName(String prefix, long envId) {
        return prefix + String.valueOf(envId);
    }
}
