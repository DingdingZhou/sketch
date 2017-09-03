/*
 * Copyright (C) 2013 Peng fei Pan <sky@xiaopan.me>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.xiaopan.sketch.request;

import android.content.Context;

import me.xiaopan.sketch.Configuration;
import me.xiaopan.sketch.SLog;
import me.xiaopan.sketch.Sketch;
import me.xiaopan.sketch.uri.UriModel;

public abstract class BaseRequest {
    private String uri;
    private UriModel uriModel;
    private String key;
    private String diskCacheKey;
    private Sketch sketch;
    private String logName = "Request";
    private Status status;
    private ErrorCause errorCause;
    private CancelCause cancelCause;

    BaseRequest(Sketch sketch, String uri, UriModel uriModel, String key) {
        this.sketch = sketch;
        this.uri = uri;
        this.uriModel = uriModel;
        this.key = key;
    }

    public Sketch getSketch() {
        return sketch;
    }

    public Context getContext() {
        return sketch.getConfiguration().getContext();
    }

    public Configuration getConfiguration() {
        return sketch.getConfiguration();
    }

    public String getUri() {
        return uri;
    }

    public UriModel getUriModel() {
        return uriModel;
    }

    public String getKey() {
        return key;
    }

    public String getDiskCacheKey() {
        if (diskCacheKey == null) {
            diskCacheKey = uriModel.getDiskCacheKey(uri);
        }
        return diskCacheKey;
    }

    /**
     * 获取日志名称
     */
    public String getLogName() {
        return logName;
    }

    /**
     * 设置日志名称
     */
    void setLogName(String logName) {
        this.logName = logName;
    }

    /**
     * 获取状态
     */
    @SuppressWarnings("unused")
    public Status getStatus() {
        return status;
    }

    /**
     * 设置状态
     */
    public void setStatus(Status status) {
        this.status = status;
        if (SLog.isLoggable(SLog.LEVEL_DEBUG | SLog.TYPE_FLOW)) {
            if (status == Status.FAILED) {
                SLog.d(getLogName(), "new status. %s. %s", status.getLog(), errorCause != null ? errorCause.name() : "");
            } else if (status == Status.CANCELED) {
                SLog.d(getLogName(), "new status. %s. %s", status.getLog(), cancelCause != null ? cancelCause.name() : "");
            } else {
                SLog.d(getLogName(), "new status. %s", (status != null ? status.getLog() : ""));
            }
        }
    }

    /**
     * 获取失败原因
     */
    public ErrorCause getErrorCause() {
        return errorCause;
    }

    /**
     * 设置失败原因
     */
    @SuppressWarnings("unused")
    protected void setErrorCause(ErrorCause errorCause) {
        this.errorCause = errorCause;
    }

    /**
     * 获取取消原因
     */
    public CancelCause getCancelCause() {
        return cancelCause;
    }

    /**
     * 设置取消原因
     */
    protected void setCancelCause(CancelCause cancelCause) {
        this.cancelCause = cancelCause;
    }

    /**
     * 请求是否已经结束了
     */
    public boolean isFinished() {
        return status == null || status == Status.COMPLETED || status == Status.CANCELED || status == Status.FAILED;
    }

    /**
     * 请求是不是已经取消了
     */
    // TODO: 2017/9/3 非请求里只要调用这个方法的都抛出 CanceledException
    public boolean isCanceled() {
        return status == Status.CANCELED;
    }

    /**
     * 失败了
     */
    protected void error(ErrorCause errorCause) {
        setErrorCause(errorCause);
        setStatus(Status.FAILED);
    }

    /**
     * 取消了
     */
    // TODO: 2017/8/27 重新梳理这个canceled方法和cancel方法，现在看来设计的不够清晰
    protected void canceled(CancelCause cancelCause) {
        setCancelCause(cancelCause);
        setStatus(Status.CANCELED);
    }

    /**
     * 取消请求
     *
     * @return false：请求已经结束了
     */
    public boolean cancel(CancelCause cancelCause) {
        if (!isFinished()) {
            canceled(cancelCause);
            return true;
        } else {
            return false;
        }
    }

    // TODO: 2017/9/3 都替换成这个方法
    public String getThreadName(){
        return Thread.currentThread().getName();
    }

    /**
     * 请求的状态
     */
    public enum Status {
        /**
         * 等待分发
         */
        WAIT_DISPATCH("waitDispatch"),

        /**
         * 开始分发
         */
        START_DISPATCH("startDispatch"),

        /**
         * 拦截本地任务
         */
        INTERCEPT_LOCAL_TASK("interceptLocalTask"),


        /**
         * 等待下载
         */
        WAIT_DOWNLOAD("waitDownload"),

        /**
         * 开始下载
         */
        START_DOWNLOAD("startDownload"),

        /**
         * 检查磁盘缓存
         */
        CHECK_DISK_CACHE("checkDiskCache"),

        /**
         * 连接中
         */
        CONNECTING("connecting"),

        /**
         * 读取数据
         */
        READ_DATA("readData"),


        /**
         * 等待加载
         */
        WAIT_LOAD("waitLoad"),

        /**
         * 开始加载
         */
        START_LOAD("startLoad"),

        /**
         * 获取内存缓存编辑锁
         */
        GET_MEMORY_CACHE_EDIT_LOCK("getMemoryCacheEditLock"),

        /**
         * 检查内存缓存
         */
        CHECK_MEMORY_CACHE("checkMemoryCache"),

        /**
         * 解码中
         */
        DECODING("decoding"),

        /**
         * 处理中
         */
        PROCESSING("processing"),

        /**
         * 等待显示
         */
        WAIT_DISPLAY("waitDisplay"),


        /**
         * 已完成
         */
        COMPLETED("completed"),

        /**
         * 已失败
         */
        FAILED("failed"),

        /**
         * 已取消
         */
        CANCELED("canceled"),;

        private String log;

        Status(String log) {
            this.log = log;
        }

        public String getLog() {
            return log;
        }
    }
}
