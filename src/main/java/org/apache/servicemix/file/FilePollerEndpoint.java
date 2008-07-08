/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.servicemix.file;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;

import javax.jbi.JBIException;
import javax.jbi.management.DeploymentException;
import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.InOnly;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.jbi.servicedesc.ServiceEndpoint;
import javax.xml.namespace.QName;

import org.apache.servicemix.common.DefaultComponent;
import org.apache.servicemix.common.ServiceUnit;
import org.apache.servicemix.common.endpoints.PollingEndpoint;
import org.apache.servicemix.components.util.DefaultFileMarshaler;
import org.apache.servicemix.components.util.FileMarshaler;
import org.apache.servicemix.locks.LockManager;
import org.apache.servicemix.locks.impl.SimpleLockManager;

/**
 * A polling endpoint which looks for a file or files in a directory
 * and sends the files into the JBI bus as messages, deleting the files
 * by default when they are processed.
 *
 * @org.apache.xbean.XBean element="poller"
 *
 * @version $Revision$
 */
public class FilePollerEndpoint extends PollingEndpoint implements FileEndpointType {

    private File file;
    private FileFilter filter;
    private boolean deleteFile = true;
    private boolean recursive = true;
    private boolean autoCreateDirectory = true;
    private File archive;
    private FileMarshaler marshaler = new DefaultFileMarshaler();
    private LockManager lockManager;
    private ConcurrentMap<String, File> openExchanges;

    public FilePollerEndpoint() {
    }

    public FilePollerEndpoint(ServiceUnit serviceUnit, QName service, String endpoint) {
        super(serviceUnit, service, endpoint);
    }

    public FilePollerEndpoint(DefaultComponent component, ServiceEndpoint endpoint) {
        super(component, endpoint);
    }

    /* (non-Javadoc)
     * @see org.apache.servicemix.common.endpoints.PollingEndpoint#start()
     */
    @Override
    public synchronized void start() throws Exception {
        super.start();
        
        // create the openExchanges map
        this.openExchanges = new ConcurrentHashMap<String, File>();
    }
    
    public void poll() throws Exception {
        pollFileOrDirectory(file);
    }

    public void validate() throws DeploymentException {
        super.validate();
        if (file == null) {
            throw new DeploymentException("You must specify a file property");
        }
        if (isAutoCreateDirectory() && !file.exists()) {
            file.mkdirs();
        }
        if (archive != null) {
            if (!deleteFile) {
                throw new DeploymentException("Archive shouldn't be specified unless deleteFile='true'");
            }
            if (isAutoCreateDirectory() && !archive.exists()) {
                archive.mkdirs();
            }
            if (!archive.isDirectory()) {
                throw new DeploymentException("Archive should refer to a directory");
            }
        }
        if (lockManager == null) {
            lockManager = createLockManager();
        }
    }
    
    protected LockManager createLockManager() {
        return new SimpleLockManager();
    }


    // Properties
    //-------------------------------------------------------------------------
    public File getFile() {
        return file;
    }

    /**
     * Sets the file to poll, which can be a directory or a file.
     *
     * @param file
     */
    public void setFile(File file) {
        this.file = file;
    }

    /**
     * @return the lockManager
     */
    public LockManager getLockManager() {
        return lockManager;
    }

    /**
     * @param lockManager the lockManager to set
     */
    public void setLockManager(LockManager lockManager) {
        this.lockManager = lockManager;
    }

    public FileFilter getFilter() {
        return filter;
    }

    /**
     * Sets the optional filter to choose which files to process
     */
    public void setFilter(FileFilter filter) {
        this.filter = filter;
    }

    /**
     * Returns whether or not we should delete the file when its processed
     */
    public boolean isDeleteFile() {
        return deleteFile;
    }

    public void setDeleteFile(boolean deleteFile) {
        this.deleteFile = deleteFile;
    }

    public boolean isRecursive() {
        return recursive;
    }

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    public boolean isAutoCreateDirectory() {
        return autoCreateDirectory;
    }

    public void setAutoCreateDirectory(boolean autoCreateDirectory) {
        this.autoCreateDirectory = autoCreateDirectory;
    }

    public FileMarshaler getMarshaler() {
        return marshaler;
    }

    public void setMarshaler(FileMarshaler marshaler) {
        this.marshaler = marshaler;
    }
    
    public File getArchive() {
        return archive;
    }
    
    /**
     * Configure a directory to archive files before deleting them.
     * 
     * @param archive the archive directory
     */
    public void setArchive(File archive) {
        this.archive = archive;
    }
    
    // Implementation methods
    //-------------------------------------------------------------------------


    protected void pollFileOrDirectory(File fileOrDirectory) {
        pollFileOrDirectory(fileOrDirectory, true);
    }

    protected void pollFileOrDirectory(File fileOrDirectory, boolean processDir) {
        if (!fileOrDirectory.isDirectory()) {
            pollFile(fileOrDirectory); // process the file
        } else if (processDir) {
            logger.debug("Polling directory " + fileOrDirectory);
            File[] files = fileOrDirectory.listFiles(getFilter());
            for (int i = 0; i < files.length; i++) {
                pollFileOrDirectory(files[i], isRecursive()); // self-recursion
            }
        } else {
            logger.debug("Skipping directory " + fileOrDirectory);
        }
    }

    protected void pollFile(final File aFile) {
        if (logger.isDebugEnabled()) {
            logger.debug("Scheduling file " + aFile + " for processing");
        }
        getExecutor().execute(new Runnable() {
            public void run() {
                String uri = file.toURI().relativize(aFile.toURI()).toString();
                Lock lock = lockManager.getLock(uri);
                if (lock.tryLock()) {
                    processFileNow(aFile);
                } else {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Unable to acquire lock on " + aFile);
                    }
                }
            }
        });
    }

    protected void processFileNow(File aFile) {
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Processing file " + aFile);
            }
            if (aFile.exists()) {
                processFile(aFile);
            }
        } catch (Exception e) {
            logger.error("Failed to process file: " + aFile + ". Reason: " + e, e);
        }
    }

    protected void processFile(File aFile) throws Exception {
        InputStream in = null;
        String name = aFile.getCanonicalPath();
        in = new BufferedInputStream(new FileInputStream(aFile));
        InOnly exchange = getExchangeFactory().createInOnlyExchange();
        configureExchangeTarget(exchange);
        NormalizedMessage message = exchange.createMessage();
        exchange.setInMessage(message);
        marshaler.readMessage(exchange, message, in, name);
        send(exchange);
        this.openExchanges.put(exchange.getExchangeId(), aFile);
    }

    public String getLocationURI() {
        return file.toURI().toString();
    }

    public void process(MessageExchange exchange) throws Exception {
        // check for done or error
        if (this.openExchanges.containsKey(exchange.getExchangeId())) {
            File aFile = this.openExchanges.get(exchange.getExchangeId());

            logger.debug("Releasing " + aFile.getAbsolutePath());
            try {
                // check for state
                if (exchange.getStatus() == ExchangeStatus.DONE) {
                    if (isDeleteFile()) {
                        if (archive != null) {
                            moveFile(aFile, archive);
                        } else {
                            if (!aFile.delete()) {
                                throw new IOException("Could not delete file " + aFile);
                            }
                        }
                    } 
                } else {
                    Exception e = exchange.getError();
                    if (e == null) {
                        e = new JBIException("Unkown error");
                    }
                    throw e;
                }
            } finally {
                // remove the open exchange
                openExchanges.remove(exchange.getExchangeId());
                // unlock the file
                unlockAsyncFile(aFile);
            }
            
        } else {
            // strange, we don't know this exchange
            logger.debug("Received unknown exchange. Will be ignored...");
            return;
        }            
    }
    
    /**
     * unlock the file
     * 
     * @param file      the file to unlock
     */
    private void unlockAsyncFile(File file) {
        // finally remove the file from the open exchanges list
        String uri = file.toURI().relativize(file.toURI()).toString();
        Lock lock = lockManager.getLock(uri);
        if (lock != null) {
            try {
                lock.unlock();                            
            } catch (Exception ex) {
                // can't release the lock
                logger.error(ex);
            }
        }
    }

    /**
     * Move a File
     * 
     * @param src
     * @param targetDirectory
     * @throws IOException 
     */
    public static void moveFile(File src, File targetDirectory) throws IOException {
        if (!src.renameTo(new File(targetDirectory, src.getName()))) {
            throw new IOException("Failed to move " + src + " to " + targetDirectory);
        }
    }
}