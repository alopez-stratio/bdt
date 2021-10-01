/*
 * Copyright (C) 2014 Stratio (http://stratio.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.stratio.qa.models.marathon;

public class Fetchable {

    private String uri;

    private Boolean executable;

    private Boolean extract;

    private Boolean cache;

    private String destPath;

    public String getUri() {
        return uri;
    }

    public Boolean getExecutable() {
        return executable;
    }

    public Boolean getExtract() {
        return extract;
    }

    public Boolean getCache() {
        return cache;
    }

    public String getDestPath() {
        return destPath;
    }

    public void setUri(String uri)  {
        this.uri = uri;
    }

    public void setExecutable(Boolean executable) {
        this.executable = executable;
    }

    public void setExtract(Boolean extract) {
        this.extract = extract;
    }

    public void setCache(Boolean cache) {
        this.cache = cache;
    }

    public void setDestPath(String destPath) {
        this.destPath = destPath;
    }

}
