/*
 *
 *  *  Copyright 2014 Orient Technologies LTD (info(at)orientechnologies.com)
 *  *
 *  *  Licensed under the Apache License, Version 2.0 (the "License");
 *  *  you may not use this file except in compliance with the License.
 *  *  You may obtain a copy of the License at
 *  *
 *  *       http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *  Unless required by applicable law or agreed to in writing, software
 *  *  distributed under the License is distributed on an "AS IS" BASIS,
 *  *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *  See the License for the specific language governing permissions and
 *  *  limitations under the License.
 *  *
 *  * For more information: http://www.orientechnologies.com
 *
 */

package com.orientechnologies.orient.core.db.document;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Callable;

import com.orientechnologies.common.exception.OException;
import com.orientechnologies.common.listener.OListenerManger;
import com.orientechnologies.common.log.OLogManager;
import com.orientechnologies.orient.core.Orient;
import com.orientechnologies.orient.core.cache.OCacheLevelOneLocatorImpl;
import com.orientechnologies.orient.core.cache.OLocalRecordCache;
import com.orientechnologies.orient.core.command.OCommandOutputListener;
import com.orientechnologies.orient.core.command.OCommandRequest;
import com.orientechnologies.orient.core.command.OCommandRequestInternal;
import com.orientechnologies.orient.core.config.OContextConfiguration;
import com.orientechnologies.orient.core.config.OGlobalConfiguration;
import com.orientechnologies.orient.core.config.OStorageEntryConfiguration;
import com.orientechnologies.orient.core.conflict.ORecordConflictStrategy;
import com.orientechnologies.orient.core.db.*;
import com.orientechnologies.orient.core.db.record.OClassTrigger;
import com.orientechnologies.orient.core.db.record.OCurrentStorageComponentsFactory;
import com.orientechnologies.orient.core.db.record.OIdentifiable;
import com.orientechnologies.orient.core.db.record.ORecordElement;
import com.orientechnologies.orient.core.db.record.ridbag.sbtree.ORidBagDeleteHook;
import com.orientechnologies.orient.core.db.record.ridbag.sbtree.OSBTreeCollectionManager;
import com.orientechnologies.orient.core.db.record.ridbag.sbtree.OSBTreeCollectionManagerProxy;
import com.orientechnologies.orient.core.dictionary.ODictionary;
import com.orientechnologies.orient.core.exception.*;
import com.orientechnologies.orient.core.fetch.OFetchHelper;
import com.orientechnologies.orient.core.hook.OHookThreadLocal;
import com.orientechnologies.orient.core.hook.ORecordHook;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.id.ORecordId;
import com.orientechnologies.orient.core.index.OClassIndexManager;
import com.orientechnologies.orient.core.index.OIndex;
import com.orientechnologies.orient.core.index.OIndexAbstract;
import com.orientechnologies.orient.core.index.OIndexManager;
import com.orientechnologies.orient.core.intent.OIntent;
import com.orientechnologies.orient.core.iterator.ORecordIteratorClass;
import com.orientechnologies.orient.core.iterator.ORecordIteratorCluster;
import com.orientechnologies.orient.core.metadata.OMetadata;
import com.orientechnologies.orient.core.metadata.OMetadataDefault;
import com.orientechnologies.orient.core.metadata.OMetadataInternal;
import com.orientechnologies.orient.core.metadata.function.OFunctionTrigger;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.security.*;
import com.orientechnologies.orient.core.query.OQuery;
import com.orientechnologies.orient.core.record.ORecord;
import com.orientechnologies.orient.core.record.ORecordInternal;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.record.impl.ODocumentInternal;
import com.orientechnologies.orient.core.schedule.OSchedulerTrigger;
import com.orientechnologies.orient.core.serialization.serializer.OStringSerializerHelper;
import com.orientechnologies.orient.core.serialization.serializer.binary.OBinarySerializerFactory;
import com.orientechnologies.orient.core.serialization.serializer.record.ORecordSerializer;
import com.orientechnologies.orient.core.serialization.serializer.record.ORecordSerializerFactory;
import com.orientechnologies.orient.core.serialization.serializer.record.OSerializationSetThreadLocal;
import com.orientechnologies.orient.core.serialization.serializer.record.string.ORecordSerializerSchemaAware2CSV;
import com.orientechnologies.orient.core.sql.parser.OStatement;
import com.orientechnologies.orient.core.storage.*;
import com.orientechnologies.orient.core.storage.impl.local.OAbstractPaginatedStorage;
import com.orientechnologies.orient.core.storage.impl.local.OFreezableStorage;
import com.orientechnologies.orient.core.storage.impl.local.paginated.OLocalPaginatedStorage;
import com.orientechnologies.orient.core.storage.impl.local.paginated.OOfflineClusterException;
import com.orientechnologies.orient.core.storage.impl.local.paginated.ORecordSerializationContext;
import com.orientechnologies.orient.core.tx.OTransaction;
import com.orientechnologies.orient.core.tx.OTransactionNoTx;
import com.orientechnologies.orient.core.tx.OTransactionOptimistic;
import com.orientechnologies.orient.core.tx.OTransactionRealAbstract;
import com.orientechnologies.orient.core.type.tree.provider.OMVRBTreeRIDProvider;
import com.orientechnologies.orient.core.version.ORecordVersion;
import com.orientechnologies.orient.core.version.OVersionFactory;

@SuppressWarnings("unchecked")
public class ODatabaseDocumentTx extends OListenerManger<ODatabaseListener> implements ODatabaseDocumentInternal {

  @Deprecated
  private static final String                               DEF_RECORD_FORMAT = "csv";
  protected static ORecordSerializer                        defaultSerializer;
  static {
    defaultSerializer = ORecordSerializerFactory.instance().getFormat(
        OGlobalConfiguration.DB_DOCUMENT_SERIALIZER.getValueAsString());
    if (defaultSerializer == null)
      throw new ODatabaseException("Impossible to find serializer with name "
          + OGlobalConfiguration.DB_DOCUMENT_SERIALIZER.getValueAsString());
  }
  private final Map<String, Object>                         properties        = new HashMap<String, Object>();
  private final Map<ORecordHook, ORecordHook.HOOK_POSITION> unmodifiableHooks;
  protected ORecordSerializer                               serializer;
  private String                                            url;
  private OStorage                                          storage;
  private STATUS                                            status;
  private OIntent                                           currentIntent;
  private ODatabaseInternal<?>                              databaseOwner;
  private OSBTreeCollectionManager                          sbTreeCollectionManager;
  private OMetadataDefault                                  metadata;
  private OImmutableUser                                    user;
  private byte                                              recordType;
  @Deprecated
  private String                                            recordFormat;
  private Map<ORecordHook, ORecordHook.HOOK_POSITION>       hooks             = new LinkedHashMap<ORecordHook, ORecordHook.HOOK_POSITION>();
  private boolean                                           retainRecords     = true;
  private OLocalRecordCache                                 localCache;
  private boolean                                           mvcc;
  private boolean                                           validation;
  private OCurrentStorageComponentsFactory                  componentsFactory;
  private boolean                                           initialized       = false;
  private OTransaction                                      currentTx;
  private boolean                                           keepStorageOpen   = false;

  /**
   * Creates a new connection to the database.
   *
   * @param iURL
   *          of the database
   */
  public ODatabaseDocumentTx(final String iURL) {
    this(iURL, false);
  }

  public ODatabaseDocumentTx(final String iURL, boolean keepStorageOpen) {
    super(false);

    if (iURL == null)
      throw new IllegalArgumentException("URL parameter is null");

    try {
      this.keepStorageOpen = keepStorageOpen;
      url = iURL.replace('\\', '/');
      status = STATUS.CLOSED;

      // SET DEFAULT PROPERTIES
      setProperty("fetch-max", 50);

      storage = Orient.instance().loadStorage(url);

      unmodifiableHooks = Collections.unmodifiableMap(hooks);

      recordType = ODocument.RECORD_TYPE;
      localCache = new OLocalRecordCache(new OCacheLevelOneLocatorImpl());

      mvcc = OGlobalConfiguration.DB_MVCC.getValueAsBoolean();
      validation = OGlobalConfiguration.DB_VALIDATION.getValueAsBoolean();

      init();

      databaseOwner = this;
    } catch (Throwable t) {
      if (storage != null)
        Orient.instance().unregisterStorage(storage);

      throw new ODatabaseException("Error on opening database '" + iURL + "'", t);
    }

    setSerializer(defaultSerializer);
  }

  /**
   * @return default serializer which is used to serialize documents. Default serializer is common for all database instances.
   */
  public static ORecordSerializer getDefaultSerializer() {
    return defaultSerializer;
  }

  /**
   * Sets default serializer. The default serializer is common for all database instances.
   * 
   * @param iDefaultSerializer
   *          new default serializer value
   */
  public static void setDefaultSerializer(ORecordSerializer iDefaultSerializer) {
    defaultSerializer = iDefaultSerializer;
  }

  /**
   * Opens connection to the storage with given user and password.
   *
   * But we do suggest {@link com.orientechnologies.orient.core.db.OPartitionedDatabasePool#acquire()} instead. It will make work
   * faster even with embedded database.
   *
   * @param iUserName
   *          Username to login
   * @param iUserPassword
   *          Password associated to the user
   *
   * @return Current database instance.
   */
  @Override
  public <DB extends ODatabase> DB open(final String iUserName, final String iUserPassword) {
    setCurrentDatabaseInThreadLocal();

    try {
      if (status == STATUS.OPEN)
        throw new IllegalStateException("Database " + getName() + " is already open");

      if (user != null && !user.getName().equals(iUserName))
        initialized = false;

      if (storage.isClosed()) {
        storage.open(iUserName, iUserPassword, properties);
      } else if (storage instanceof OStorageProxy) {
        final String name = ((OStorageProxy) storage).getUserName();
        if (!name.equals(iUserName)) {
          storage.close();
          storage.open(iUserName, iUserPassword, properties);
        }
      }

      status = STATUS.OPEN;

      initAtFirstOpen(iUserName, iUserPassword);

      if (!(getStorage() instanceof OStorageProxy)) {
        final OSecurity security = metadata.getSecurity();
        if (user == null || user.getVersion() != security.getVersion() || !user.getName().equalsIgnoreCase(iUserName)) {
          final OUser usr = metadata.getSecurity().authenticate(iUserName, iUserPassword);
          if (usr != null)
            user = new OImmutableUser(security.getVersion(), usr);
          else
            user = null;

          checkSecurity(ORule.ResourceGeneric.DATABASE, ORole.PERMISSION_READ);
        }
      }

      // WAKE UP LISTENERS
      callOnOpenListeners();
    } catch (OException e) {
      close();
      throw e;
    } catch (Exception e) {
      close();
      throw new ODatabaseException("Cannot open database", e);
    }
    return (DB) this;
  }

  /**
   * Opens a database using an authentication token received as an argument.
   *
   * @param iToken
   *          Authentication token
   * @return The Database instance itself giving a "fluent interface". Useful to call multiple methods in chain.
   */
  public <DB extends ODatabase> DB open(final OToken iToken) {
    setCurrentDatabaseInThreadLocal();

    try {
      if (status == STATUS.OPEN)
        throw new IllegalStateException("Database " + getName() + " is already open");

      if (user != null && !user.getIdentity().equals(iToken.getUserId()))
        initialized = false;

      if (storage instanceof OStorageProxy) {
        throw new ODatabaseException("Cannot use a token open on remote database");
      }
      if (storage.isClosed()) {
        // i don't have username and password at this level, anyway the storage embedded don't really need it
        storage.open("", "", properties);
      }

      status = STATUS.OPEN;

      initAtFirstOpen(null, null);

      final OSecurity security = metadata.getSecurity();
      if (user == null || user.getVersion() != security.getVersion()) {
        final OUser usr = metadata.getSecurity().authenticate(iToken);
        if (usr != null)
          user = new OImmutableUser(security.getVersion(), usr);
        else
          user = null;

        checkSecurity(ORule.ResourceGeneric.DATABASE, ORole.PERMISSION_READ);
      }

      // WAKE UP LISTENERS
      callOnOpenListeners();
    } catch (OException e) {
      close();
      throw e;
    } catch (Exception e) {
      close();
      throw new ODatabaseException("Cannot open database", e);
    }
    return (DB) this;
  }

  public void callOnOpenListeners() {
    // WAKE UP DB LIFECYCLE LISTENER
    for (Iterator<ODatabaseLifecycleListener> it = Orient.instance().getDbLifecycleListeners(); it.hasNext();)
      it.next().onOpen(getDatabaseOwner());

    // WAKE UP LISTENERS
    for (ODatabaseListener listener : getListenersCopy())
      try {
        listener.onOpen(getDatabaseOwner());
      } catch (Throwable t) {
        t.printStackTrace();
      }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <DB extends ODatabase> DB create() {
    return create(null);
  }

  @Override
  public <DB extends ODatabase> DB create(final Map<OGlobalConfiguration, Object> iInitialSettings) {
    setCurrentDatabaseInThreadLocal();

    try {
      if (status == STATUS.OPEN)
        throw new IllegalStateException("Database " + getName() + " is already open");

      if (storage == null)
        storage = Orient.instance().loadStorage(url);

      if (iInitialSettings != null) {
        // SETUP INITIAL SETTINGS
        final OContextConfiguration ctxCfg = storage.getConfiguration().getContextConfiguration();
        for (Map.Entry<OGlobalConfiguration, Object> e : iInitialSettings.entrySet()) {
          ctxCfg.setValue(e.getKey(), e.getValue());
        }
      }

      storage.create(properties);

      status = STATUS.OPEN;

      componentsFactory = getStorage().getComponentsFactory();

      sbTreeCollectionManager = new OSBTreeCollectionManagerProxy(this, getStorage().getResource(
          OSBTreeCollectionManager.class.getSimpleName(), new Callable<OSBTreeCollectionManager>() {
            @Override
            public OSBTreeCollectionManager call() throws Exception {
              Class<? extends OSBTreeCollectionManager> managerClass = getStorage().getCollectionManagerClass();

              if (managerClass == null) {
                OLogManager.instance().warn(this, "Current implementation of storage does not support sbtree collections");
                return null;
              } else {
                return managerClass.newInstance();
              }
            }
          }));
      localCache.startup();

      getStorage().getConfiguration().setRecordSerializer(getSerializer().toString());
      getStorage().getConfiguration().setRecordSerializerVersion(getSerializer().getCurrentVersion());

      //since 2.1 newly created databases use strinct SQL validation by default
      getStorage().getConfiguration().properties.add(new OStorageEntryConfiguration(OStatement.CUSTOM_STRICT_SQL, "true"));

      getStorage().getConfiguration().update();

      if (!(getStorage() instanceof OStorageProxy))
        installHooks();

      // CREATE THE DEFAULT SCHEMA WITH DEFAULT USER
      metadata = new OMetadataDefault();
      metadata.create();

      registerHook(new OSecurityTrackerHook(metadata.getSecurity(), this), ORecordHook.HOOK_POSITION.LAST);

      final OUser usr = getMetadata().getSecurity().getUser(OUser.ADMIN);

      if (usr == null)
        user = null;
      else
        user = new OImmutableUser(getMetadata().getSecurity().getVersion(), usr);

      if (!metadata.getSchema().existsClass(OMVRBTreeRIDProvider.PERSISTENT_CLASS_NAME))
        // @COMPATIBILITY 1.0RC9
        metadata.getSchema().createClass(OMVRBTreeRIDProvider.PERSISTENT_CLASS_NAME);

      if (OGlobalConfiguration.DB_MAKE_FULL_CHECKPOINT_ON_SCHEMA_CHANGE.getValueAsBoolean())
        metadata.getSchema().setFullCheckpointOnChange(true);

      if (OGlobalConfiguration.DB_MAKE_FULL_CHECKPOINT_ON_INDEX_CHANGE.getValueAsBoolean())
        metadata.getIndexManager().setFullCheckpointOnChange(true);
      getStorage().synch();
      // WAKE UP DB LIFECYCLE LISTENER
      for (Iterator<ODatabaseLifecycleListener> it = Orient.instance().getDbLifecycleListeners(); it.hasNext();)
        it.next().onCreate(getDatabaseOwner());

      // WAKE UP LISTENERS
      for (ODatabaseListener listener : browseListeners())
        try {
          listener.onCreate(this);
        } catch (Throwable ignore) {
        }

    } catch (Exception e) {
      throw new ODatabaseException("Cannot create database '" + getName() + "'", e);
    }
    return (DB) this;
  }

  public boolean isKeepStorageOpen() {
    return keepStorageOpen;
  }

  @Override
  public void drop() {
    checkOpeness();
    checkSecurity(ORule.ResourceGeneric.DATABASE, ORole.PERMISSION_DELETE);

    setCurrentDatabaseInThreadLocal();

    callOnCloseListeners();

    if (metadata != null) {
      metadata.close();
      metadata = null;
    }

    final Iterable<ODatabaseListener> tmpListeners = getListenersCopy();
    closeOnDelete();

    try {
      if (storage == null)
        storage = Orient.instance().loadStorage(url);

      storage.delete();
      storage = null;

      // WAKE UP LISTENERS
      for (ODatabaseListener listener : tmpListeners)
        try {
          listener.onDelete(this);
        } catch (Throwable t) {
        }

      status = STATUS.CLOSED;
      ODatabaseRecordThreadLocal.INSTANCE.remove();

    } catch (OException e) {
      // PASS THROUGH
      throw e;
    } catch (Exception e) {
      throw new ODatabaseException("Cannot delete database", e);
    }
  }

  public void callOnCloseListeners() {
    // WAKE UP DB LIFECYCLE LISTENER
    for (Iterator<ODatabaseLifecycleListener> it = Orient.instance().getDbLifecycleListeners(); it.hasNext();)
      it.next().onClose(getDatabaseOwner());

    // WAKE UP LISTENERS
    for (ODatabaseListener listener : getListenersCopy())
      try {
        listener.onClose(getDatabaseOwner());
      } catch (Throwable t) {
        t.printStackTrace();
      }
  }

  /**
   * {@inheritDoc}
   */
  public <RET extends ORecord> RET getRecord(final OIdentifiable iIdentifiable) {
    if (iIdentifiable instanceof ORecord)
      return (RET) iIdentifiable;
    return (RET) load(iIdentifiable.getIdentity());
  }

  @Override
  public void reload() {
    metadata.reload();
    storage.reload();
  }

  /**
   * {@inheritDoc}
   */
  public <RET extends ORecord> RET load(final ORID iRecordId, final String iFetchPlan, final boolean iIgnoreCache) {
    return (RET) executeReadRecord((ORecordId) iRecordId, null, iFetchPlan, iIgnoreCache, false, OStorage.LOCKING_STRATEGY.DEFAULT);
  }

  /**
   * Deletes the record checking the version.
   */
  public ODatabase<ORecord> delete(final ORID iRecord, final ORecordVersion iVersion) {
    executeDeleteRecord(iRecord, iVersion, true, true, OPERATION_MODE.SYNCHRONOUS, false);
    return this;
  }

  public ODatabase<ORecord> cleanOutRecord(final ORID iRecord, final ORecordVersion iVersion) {
    executeDeleteRecord(iRecord, iVersion, true, true, OPERATION_MODE.SYNCHRONOUS, true);
    return this;
  }

  public ORecord getRecordByUserObject(final Object iUserObject, final boolean iCreateIfNotAvailable) {
    return (ORecord) iUserObject;
  }

  public void registerUserObject(final Object iObject, final ORecord iRecord) {
  }

  public void registerUserObjectAfterLinkSave(ORecord iRecord) {
  }

  public Object getUserObjectByRecord(final OIdentifiable record, final String iFetchPlan) {
    return record;
  }

  public boolean existsUserObjectByRID(final ORID iRID) {
    return true;
  }

  public String getType() {
    return TYPE;
  }

  /**
   * Deletes the record without checking the version.
   */
  public ODatabaseDocument delete(final ORID iRecord, final OPERATION_MODE iMode) {
    ORecord record = iRecord.getRecord();
    if (record == null)
      return this;

    delete(record, iMode);
    return this;
  }

  public ODatabaseDocument delete(final ORecord iRecord, final OPERATION_MODE iMode) {
    currentTx.deleteRecord(iRecord, iMode);
    return this;
  }

  public <REC extends ORecord> ORecordIteratorCluster<REC> browseCluster(final String iClusterName, final Class<REC> iClass) {
    checkSecurity(ORule.ResourceGeneric.CLUSTER, ORole.PERMISSION_READ, iClusterName);

    setCurrentDatabaseInThreadLocal();

    final int clusterId = getClusterIdByName(iClusterName);

    return new ORecordIteratorCluster<REC>(this, this, clusterId, true);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <REC extends ORecord> ORecordIteratorCluster<REC> browseCluster(final String iClusterName, final Class<REC> iRecordClass,
      final long startClusterPosition, final long endClusterPosition, final boolean loadTombstones) {
    checkSecurity(ORule.ResourceGeneric.CLUSTER, ORole.PERMISSION_READ, iClusterName);

    setCurrentDatabaseInThreadLocal();

    final int clusterId = getClusterIdByName(iClusterName);

    return new ORecordIteratorCluster<REC>(this, this, clusterId, startClusterPosition, endClusterPosition, true, loadTombstones,
        OStorage.LOCKING_STRATEGY.DEFAULT);
  }

  /**
   * {@inheritDoc}
   */
  public OCommandRequest command(final OCommandRequest iCommand) {
    checkSecurity(ORule.ResourceGeneric.COMMAND, ORole.PERMISSION_READ);

    setCurrentDatabaseInThreadLocal();

    final OCommandRequestInternal command = (OCommandRequestInternal) iCommand;

    try {
      command.reset();
      return command;

    } catch (Exception e) {
      throw new ODatabaseException("Error on command execution", e);
    }
  }

  /**
   * {@inheritDoc}
   */
  public <RET extends List<?>> RET query(final OQuery<?> iCommand, final Object... iArgs) {
    setCurrentDatabaseInThreadLocal();

    iCommand.reset();
    return (RET) iCommand.execute(iArgs);
  }

  /**
   * {@inheritDoc}
   */
  public byte getRecordType() {
    return recordType;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long countClusterElements(final int[] iClusterIds) {
    return countClusterElements(iClusterIds, false);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long countClusterElements(final int iClusterId) {
    return countClusterElements(iClusterId, false);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long countClusterElements(int iClusterId, boolean countTombstones) {
    final String name = getClusterNameById(iClusterId);
    if (name == null)
      return 0;
    checkSecurity(ORule.ResourceGeneric.CLUSTER, ORole.PERMISSION_READ, name);
    setCurrentDatabaseInThreadLocal();

    return storage.count(iClusterId, countTombstones);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long countClusterElements(int[] iClusterIds, boolean countTombstones) {
    String name;
    for (int iClusterId : iClusterIds) {
      name = getClusterNameById(iClusterId);
      checkSecurity(ORule.ResourceGeneric.CLUSTER, ORole.PERMISSION_READ, name);
    }

    return storage.count(iClusterIds, countTombstones);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long countClusterElements(final String iClusterName) {
    checkSecurity(ORule.ResourceGeneric.CLUSTER, ORole.PERMISSION_READ, iClusterName);
    setCurrentDatabaseInThreadLocal();

    final int clusterId = getClusterIdByName(iClusterName);
    if (clusterId < 0)
      throw new IllegalArgumentException("Cluster '" + iClusterName + "' was not found");
    return storage.count(clusterId);
  }

  /**
   * {@inheritDoc}
   */
  public OMetadataDefault getMetadata() {
    checkOpeness();
    return metadata;
  }

  /**
   * {@inheritDoc}
   */
  public <DB extends ODatabaseDocument> DB checkSecurity(ORule.ResourceGeneric resourceGeneric, String resourceSpecific,
      final int iOperation) {
    if (user != null) {
      try {
        user.allow(resourceGeneric, resourceSpecific, iOperation);
      } catch (OSecurityAccessException e) {

        if (OLogManager.instance().isDebugEnabled())
          OLogManager.instance().debug(this,
              "[checkSecurity] User '%s' tried to access to the reserved resource '%s', operation '%s'", getUser(),
              resourceGeneric + "." + resourceSpecific, iOperation);

        throw e;
      }
    }
    return (DB) this;
  }

  /**
   * {@inheritDoc}
   */
  public <DB extends ODatabaseDocument> DB checkSecurity(final ORule.ResourceGeneric iResourceGeneric, final int iOperation,
      final Object... iResourcesSpecific) {

    if (user != null) {
      try {
        for (Object target : iResourcesSpecific) {
          if (target != null) {
            user.allow(iResourceGeneric, target.toString(), iOperation);
          } else
            user.allow(iResourceGeneric, null, iOperation);
        }
      } catch (OSecurityAccessException e) {
        if (OLogManager.instance().isDebugEnabled())
          OLogManager.instance().debug(this,
              "[checkSecurity] User '%s' tried to access to the reserved resource '%s', target(s) '%s', operation '%s'", getUser(),
              iResourceGeneric, Arrays.toString(iResourcesSpecific), iOperation);

        throw e;
      }
    }
    return (DB) this;
  }

  /**
   * {@inheritDoc}
   */
  public <DB extends ODatabaseDocument> DB checkSecurity(final ORule.ResourceGeneric iResourceGeneric, final int iOperation,
      final Object iResourceSpecific) {
    checkOpeness();
    if (user != null) {
      try {
        if (iResourceSpecific != null)
          user.allow(iResourceGeneric, iResourceSpecific.toString(), iOperation);
        else
          user.allow(iResourceGeneric, null, iOperation);
      } catch (OSecurityAccessException e) {
        if (OLogManager.instance().isDebugEnabled())
          OLogManager.instance().debug(this,
              "[checkSecurity] User '%s' tried to access to the reserved resource '%s', target '%s', operation '%s'", getUser(),
              iResourceGeneric, iResourceSpecific, iOperation);

        throw e;
      }
    }
    return (DB) this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ODatabaseInternal<?> getDatabaseOwner() {
    ODatabaseInternal<?> current = databaseOwner;

    while (current != null && current != this && current.getDatabaseOwner() != current)
      current = current.getDatabaseOwner();

    return current;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ODatabaseInternal<ORecord> setDatabaseOwner(ODatabaseInternal<?> iOwner) {
    databaseOwner = iOwner;
    return this;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isRetainRecords() {
    return retainRecords;
  }

  /**
   * {@inheritDoc}
   */
  public ODatabaseDocument setRetainRecords(boolean retainRecords) {
    this.retainRecords = retainRecords;
    return this;
  }

  /**
   * {@inheritDoc}
   */
  public <DB extends ODatabase> DB setStatus(final STATUS status) {
    setStatusInternal(status);
    return (DB) this;
  }

  public void setStatusInternal(final STATUS status) {
    this.status = status;
  }

  public void setDefaultClusterIdInternal(final int iDefClusterId) {
    getStorage().setDefaultClusterId(iDefClusterId);
  }

  /**
   * {@inheritDoc}
   */
  public void setInternal(final ATTRIBUTES iAttribute, final Object iValue) {
    set(iAttribute, iValue);
  }

  /**
   * {@inheritDoc}
   */
  public OSecurityUser getUser() {
    return user;
  }

  /**
   * {@inheritDoc}
   */
  public void setUser(OSecurityUser user) {
    if (user instanceof OUser) {
      OMetadata metadata = getMetadata();
      if (metadata != null) {
        final OSecurity security = metadata.getSecurity();
        this.user = new OImmutableUser(security.getVersion(), (OUser) user);
      } else
        this.user = new OImmutableUser(-1, (OUser) user);
    } else
      this.user = (OImmutableUser) user;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isMVCC() {
    return mvcc;
  }

  /**
   * {@inheritDoc}
   */
  public <DB extends ODatabase<?>> DB setMVCC(boolean mvcc) {
    this.mvcc = mvcc;
    return (DB) this;
  }

  /**
   * {@inheritDoc}
   */
  public ODictionary<ORecord> getDictionary() {
    checkOpeness();
    return metadata.getIndexManager().getDictionary();
  }

  /**
   * {@inheritDoc}
   */
  public <DB extends ODatabase<?>> DB registerHook(final ORecordHook iHookImpl, final ORecordHook.HOOK_POSITION iPosition) {
    final Map<ORecordHook, ORecordHook.HOOK_POSITION> tmp = new LinkedHashMap<ORecordHook, ORecordHook.HOOK_POSITION>(hooks);
    tmp.put(iHookImpl, iPosition);
    hooks.clear();
    for (ORecordHook.HOOK_POSITION p : ORecordHook.HOOK_POSITION.values()) {
      for (Map.Entry<ORecordHook, ORecordHook.HOOK_POSITION> e : tmp.entrySet()) {
        if (e.getValue() == p)
          hooks.put(e.getKey(), e.getValue());
      }
    }
    return (DB) this;
  }

  /**
   * {@inheritDoc}
   */
  public <DB extends ODatabase<?>> DB registerHook(final ORecordHook iHookImpl) {
    return (DB) registerHook(iHookImpl, ORecordHook.HOOK_POSITION.REGULAR);
  }

  /**
   * {@inheritDoc}
   */
  public <DB extends ODatabase<?>> DB unregisterHook(final ORecordHook iHookImpl) {
    if (iHookImpl != null) {
      iHookImpl.onUnregister();
      hooks.remove(iHookImpl);
    }
    return (DB) this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public OLocalRecordCache getLocalCache() {
    return localCache;
  }

  /**
   * {@inheritDoc}
   */
  public Map<ORecordHook, ORecordHook.HOOK_POSITION> getHooks() {
    return unmodifiableHooks;
  }

  /**
   * Callback the registeted hooks if any.
   *
   * @param type
   *          Hook type. Define when hook is called.
   * @param id
   *          Record received in the callback
   * @return True if the input record is changed, otherwise false
   */
  public ORecordHook.RESULT callbackHooks(final ORecordHook.TYPE type, final OIdentifiable id) {
    if (id == null || !OHookThreadLocal.INSTANCE.push(id))
      return ORecordHook.RESULT.RECORD_NOT_CHANGED;

    try {
      final ORecord rec = id.getRecord();
      if (rec == null)
        return ORecordHook.RESULT.RECORD_NOT_CHANGED;

      final OScenarioThreadLocal.RUN_MODE runMode = OScenarioThreadLocal.INSTANCE.get();

      boolean recordChanged = false;
      for (ORecordHook hook : hooks.keySet()) {
        // CHECK IF EXECUTE THE TRIGGER BASED ON STORAGE TYPE: DISTRIBUTED OR NOT
        switch (runMode) {
        case DEFAULT: // NON_DISTRIBUTED OR PROXIED DB
          if (getStorage().isDistributed()
              && hook.getDistributedExecutionMode() == ORecordHook.DISTRIBUTED_EXECUTION_MODE.TARGET_NODE)
            // SKIP
            continue;
          break; // TARGET NODE
        case RUNNING_DISTRIBUTED:
          if (hook.getDistributedExecutionMode() == ORecordHook.DISTRIBUTED_EXECUTION_MODE.SOURCE_NODE)
            continue;
        }

        final ORecordHook.RESULT res = hook.onTrigger(type, rec);

        if (res == ORecordHook.RESULT.RECORD_CHANGED)
          recordChanged = true;
        else if (res == ORecordHook.RESULT.SKIP_IO)
          // SKIP IO OPERATION
          return res;
        else if (res == ORecordHook.RESULT.SKIP)
          // SKIP NEXT HOOKS AND RETURN IT
          return res;
        else if (res == ORecordHook.RESULT.RECORD_REPLACED)
          return res;
      }

      return recordChanged ? ORecordHook.RESULT.RECORD_CHANGED : ORecordHook.RESULT.RECORD_NOT_CHANGED;

    } finally {
      OHookThreadLocal.INSTANCE.pop(id);
    }
  }

  /**
   * {@inheritDoc}
   */
  public boolean isValidationEnabled() {
    return !getStatus().equals(STATUS.IMPORTING) && validation;
  }

  /**
   * {@inheritDoc}
   */
  public <DB extends ODatabaseDocument> DB setValidationEnabled(final boolean iEnabled) {
    validation = iEnabled;
    return (DB) this;
  }

  public ORecordConflictStrategy getConflictStrategy() {
    return getStorage().getConflictStrategy();
  }

  public ODatabaseDocumentTx setConflictStrategy(final ORecordConflictStrategy iResolver) {
    getStorage().setConflictStrategy(iResolver);
    return this;
  }

  public ODatabaseDocumentTx setConflictStrategy(final String iStrategyName) {
    getStorage().setConflictStrategy(Orient.instance().getRecordConflictStrategy().getStrategy(iStrategyName));
    return this;
  }

  @Override
  public OContextConfiguration getConfiguration() {
    if (storage != null)
      return storage.getConfiguration().getContextConfiguration();
    return null;
  }

  @Override
  public boolean declareIntent(final OIntent iIntent) {
    if (currentIntent != null) {
      if (iIntent != null && iIntent.getClass().equals(currentIntent.getClass()))
        // SAME INTENT: JUMP IT
        return false;

      // END CURRENT INTENT
      currentIntent.end(this);
    }

    currentIntent = iIntent;

    if (iIntent != null)
      iIntent.begin(this);

    return true;
  }

  @Override
  public boolean exists() {
    if (status == STATUS.OPEN)
      return true;

    if (storage == null)
      storage = Orient.instance().loadStorage(url);

    return storage.exists();
  }

  @Override
  public void close() {
    localCache.shutdown();

    if (isClosed())
      return;

    setCurrentDatabaseInThreadLocal();
    try {
      commit(true);
    } catch (Exception e) {
      OLogManager.instance().error(this, "Exception during commit of active transaction.", e);
    }

    if (status != STATUS.OPEN)
      return;

    callOnCloseListeners();

    if (currentIntent != null) {
      currentIntent.end(this);
      currentIntent = null;
    }

    status = STATUS.CLOSED;

    localCache.clear();

    if (!keepStorageOpen && storage != null)
      storage.close();

    ODatabaseRecordThreadLocal.INSTANCE.remove();
  }

  @Override
  public STATUS getStatus() {
    return status;
  }

  @Override
  public long getSize() {
    return storage.getSize();
  }

  @Override
  public String getName() {
    return storage != null ? storage.getName() : url;
  }

  @Override
  public String getURL() {
    return url != null ? url : storage.getURL();
  }

  @Override
  public int getDefaultClusterId() {
    return storage.getDefaultClusterId();
  }

  @Override
  public int getClusters() {
    return storage.getClusters();
  }

  @Override
  public boolean existsCluster(final String iClusterName) {
    return storage.getClusterNames().contains(iClusterName.toLowerCase());
  }

  @Override
  public Collection<String> getClusterNames() {
    return storage.getClusterNames();
  }

  @Override
  public int getClusterIdByName(final String iClusterName) {
    if (iClusterName == null)
      return -1;

    return storage.getClusterIdByName(iClusterName.toLowerCase());
  }

  @Override
  public String getClusterNameById(final int iClusterId) {
    if (iClusterId == -1)
      return null;

    // PHIYSICAL CLUSTER
    return storage.getPhysicalClusterNameById(iClusterId);
  }

  @Override
  public long getClusterRecordSizeByName(final String clusterName) {
    try {
      return storage.getClusterById(getClusterIdByName(clusterName)).getRecordsSize();
    } catch (Exception e) {
      throw new ODatabaseException("Error on reading records size for cluster '" + clusterName + "'", e);
    }
  }

  @Override
  public long getClusterRecordSizeById(final int clusterId) {
    try {
      return storage.getClusterById(clusterId).getRecordsSize();
    } catch (Exception e) {
      throw new ODatabaseException("Error on reading records size for cluster with id '" + clusterId + "'", e);
    }
  }

  @Override
  public boolean isClosed() {
    return status == STATUS.CLOSED || storage.isClosed();
  }

  @Override
  public int addCluster(final String iClusterName, final Object... iParameters) {
    return storage.addCluster(iClusterName, false, iParameters);
  }

  @Override
  public int addCluster(final String iClusterName, final int iRequestedId, final Object... iParameters) {
    return storage.addCluster(iClusterName, iRequestedId, false, iParameters);
  }

  @Override
  public boolean dropCluster(final String iClusterName, final boolean iTruncate) {
    return storage.dropCluster(iClusterName, iTruncate);
  }

  @Override
  public boolean dropCluster(final int iClusterId, final boolean iTruncate) {
    return storage.dropCluster(iClusterId, iTruncate);
  }

  @Override
  public Object setProperty(final String iName, final Object iValue) {
    if (iValue == null)
      return properties.remove(iName.toLowerCase());
    else
      return properties.put(iName.toLowerCase(), iValue);
  }

  @Override
  public Object getProperty(final String iName) {
    return properties.get(iName.toLowerCase());
  }

  @Override
  public Iterator<Map.Entry<String, Object>> getProperties() {
    return properties.entrySet().iterator();
  }

  @Override
  public Object get(final ATTRIBUTES iAttribute) {
    if (iAttribute == null)
      throw new IllegalArgumentException("attribute is null");

    switch (iAttribute) {
    case STATUS:
      return getStatus();
    case DEFAULTCLUSTERID:
      return getDefaultClusterId();
    case TYPE:
      return ((OMetadataInternal) getMetadata()).getImmutableSchemaSnapshot().existsClass("V") ? "graph" : "document";
    case DATEFORMAT:
      return storage.getConfiguration().dateFormat;

    case DATETIMEFORMAT:
      return storage.getConfiguration().dateTimeFormat;

    case TIMEZONE:
      return storage.getConfiguration().getTimeZone().getID();

    case LOCALECOUNTRY:
      return storage.getConfiguration().getLocaleCountry();

    case LOCALELANGUAGE:
      return storage.getConfiguration().getLocaleLanguage();

    case CHARSET:
      return storage.getConfiguration().getCharset();

    case CUSTOM:
      return storage.getConfiguration().properties;

    case CLUSTERSELECTION:
      return storage.getConfiguration().getClusterSelection();

    case MINIMUMCLUSTERS:
      return storage.getConfiguration().getMinimumClusters();

    case CONFLICTSTRATEGY:
      return storage.getConfiguration().getConflictStrategy();
    }

    return null;
  }

  @Override
  public <DB extends ODatabase> DB set(final ATTRIBUTES iAttribute, final Object iValue) {
    if (iAttribute == null)
      throw new IllegalArgumentException("attribute is null");

    final String stringValue = OStringSerializerHelper.getStringContent(iValue != null ? iValue.toString() : null);

    switch (iAttribute) {
    case STATUS:
      if (stringValue == null)
        throw new IllegalArgumentException("DB status can't be null");
      setStatus(STATUS.valueOf(stringValue.toUpperCase(Locale.ENGLISH)));
      break;

    case DEFAULTCLUSTERID:
      if (iValue != null) {
        if (iValue instanceof Number)
          storage.setDefaultClusterId(((Number) iValue).intValue());
        else
          storage.setDefaultClusterId(storage.getClusterIdByName(iValue.toString()));
      }
      break;

    case TYPE:
      throw new IllegalArgumentException("Database type property is not supported");

    case DATEFORMAT:
      // CHECK FORMAT
      new SimpleDateFormat(stringValue).format(new Date());

      storage.getConfiguration().dateFormat = stringValue;
      storage.getConfiguration().update();
      break;

    case DATETIMEFORMAT:
      // CHECK FORMAT
      new SimpleDateFormat(stringValue).format(new Date());

      storage.getConfiguration().dateTimeFormat = stringValue;
      storage.getConfiguration().update();
      break;

    case TIMEZONE:
      if (stringValue == null)
        throw new IllegalArgumentException("Timezone can't be null");

      storage.getConfiguration().setTimeZone(TimeZone.getTimeZone(stringValue.toUpperCase()));
      storage.getConfiguration().update();
      break;

    case LOCALECOUNTRY:
      storage.getConfiguration().setLocaleCountry(stringValue);
      storage.getConfiguration().update();
      break;

    case LOCALELANGUAGE:
      storage.getConfiguration().setLocaleLanguage(stringValue);
      storage.getConfiguration().update();
      break;

    case CHARSET:
      storage.getConfiguration().setCharset(stringValue);
      storage.getConfiguration().update();
      break;

    case CUSTOM:
      int indx = stringValue != null ? stringValue.indexOf('=') : -1;
      if (indx < 0) {
        if ("clear".equalsIgnoreCase(stringValue)) {
          clearCustomInternal();
        } else
          throw new IllegalArgumentException("Syntax error: expected <name> = <value> or clear, instead found: " + iValue);
      } else {
        String customName = stringValue.substring(0, indx).trim();
        String customValue = stringValue.substring(indx + 1).trim();
        if (customValue.isEmpty())
          removeCustomInternal(customName);
        else
          setCustomInternal(customName, customValue);
      }
      break;

    case CLUSTERSELECTION:
      storage.getConfiguration().setClusterSelection(stringValue);
      storage.getConfiguration().update();
      break;

    case MINIMUMCLUSTERS:
      if (iValue != null) {
        if (iValue instanceof Number)
          storage.getConfiguration().setMinimumClusters(((Number) iValue).intValue());
        else
          storage.getConfiguration().setMinimumClusters(Integer.parseInt(stringValue));
      } else
        // DEFAULT = 1
        storage.getConfiguration().setMinimumClusters(1);

      storage.getConfiguration().update();
      break;

    case CONFLICTSTRATEGY:
      storage.setConflictStrategy(Orient.instance().getRecordConflictStrategy().getStrategy(stringValue));
      storage.getConfiguration().setConflictStrategy(stringValue);
      storage.getConfiguration().update();
      break;

    default:
      throw new IllegalArgumentException("Option '" + iAttribute + "' not supported on alter database");

    }

    return (DB) this;
  }

  @Override
  public ORecordMetadata getRecordMetadata(final ORID rid) {
    return storage.getRecordMetadata(rid);
  }

  @Override
  public void freezeCluster(final int iClusterId) {
    freezeCluster(iClusterId, false);
  }

  @Override
  public void releaseCluster(final int iClusterId) {
    final OLocalPaginatedStorage storage;
    if (getStorage() instanceof OLocalPaginatedStorage)
      storage = ((OLocalPaginatedStorage) getStorage());
    else {
      OLogManager.instance().error(this, "We can not freeze non local storage.");
      return;
    }

    storage.release(iClusterId);
  }

  @Override
  public void freezeCluster(final int iClusterId, final boolean throwException) {
    if (getStorage() instanceof OLocalPaginatedStorage) {
      final OLocalPaginatedStorage paginatedStorage = ((OLocalPaginatedStorage) getStorage());
      paginatedStorage.freeze(throwException, iClusterId);
    } else {
      OLogManager.instance().error(this, "Only local paginated storage supports cluster freeze.");
    }
  }

  public OTransaction getTransaction() {
    return currentTx;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <RET extends ORecord> RET load(final ORecord iRecord, final String iFetchPlan) {
    return (RET) currentTx.loadRecord(iRecord.getIdentity(), iRecord, iFetchPlan, false, false, OStorage.LOCKING_STRATEGY.DEFAULT);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <RET extends ORecord> RET load(ORecord iRecord, String iFetchPlan, boolean iIgnoreCache, boolean loadTombstone,
      OStorage.LOCKING_STRATEGY iLockingStrategy) {
    return (RET) currentTx.loadRecord(iRecord.getIdentity(), iRecord, iFetchPlan, iIgnoreCache, loadTombstone, iLockingStrategy);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <RET extends ORecord> RET load(final ORecord iRecord) {
    return (RET) currentTx.loadRecord(iRecord.getIdentity(), iRecord, null, false, false, OStorage.LOCKING_STRATEGY.DEFAULT);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <RET extends ORecord> RET load(final ORID recordId) {
    return (RET) currentTx.loadRecord(recordId, null, null, false, false, OStorage.LOCKING_STRATEGY.DEFAULT);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <RET extends ORecord> RET load(final ORID iRecordId, final String iFetchPlan) {
    return (RET) currentTx.loadRecord(iRecordId, null, iFetchPlan, false, false, OStorage.LOCKING_STRATEGY.DEFAULT);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <RET extends ORecord> RET load(final ORID iRecordId, String iFetchPlan, final boolean iIgnoreCache,
      final boolean loadTombstone, OStorage.LOCKING_STRATEGY iLockingStrategy) {
    return (RET) currentTx.loadRecord(iRecordId, null, iFetchPlan, iIgnoreCache, loadTombstone, iLockingStrategy);
  }

  @SuppressWarnings("unchecked")
  public <RET extends ORecord> RET reload(final ORecord iRecord) {
    return reload(iRecord, null, false);
  }

  @SuppressWarnings("unchecked")
  public <RET extends ORecord> RET reload(final ORecord iRecord, final String iFetchPlan) {
    return reload(iRecord, iFetchPlan, false);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <RET extends ORecord> RET reload(final ORecord iRecord, final String iFetchPlan, final boolean iIgnoreCache) {
    ORecord record = currentTx.loadRecord(iRecord.getIdentity(), iRecord, iFetchPlan, iIgnoreCache, false,
        OStorage.LOCKING_STRATEGY.DEFAULT);
    if (record != null && iRecord != record) {
      iRecord.fromStream(record.toStream());
      iRecord.getRecordVersion().copyFrom(record.getRecordVersion());
    } else if (record == null)
      throw new ORecordNotFoundException("Record with rid " + iRecord.getIdentity() + " was not found in database");

    return (RET) record;
  }

  /**
   * Deletes the record without checking the version.
   */
  public ODatabaseDocument delete(final ORID iRecord) {
    checkOpeness();
    final ORecord rec = iRecord.getRecord();
    if (rec != null)
      rec.delete();
    return this;
  }

  @Override
  public boolean hide(ORID rid) {
    checkOpeness();
    if (currentTx.isActive())
      throw new ODatabaseException("This operation can be executed only in non tx mode");

    return executeHideRecord(rid, OPERATION_MODE.SYNCHRONOUS);
  }

  @Override
  public OBinarySerializerFactory getSerializerFactory() {
    return componentsFactory.binarySerializerFactory;
  }

  public ODatabaseDocument begin(final OTransaction iTx) {
    checkOpeness();
    if (currentTx.isActive() && iTx.equals(currentTx)) {
      currentTx.begin();
      return this;
    }

    currentTx.rollback(true, 0);

    // WAKE UP LISTENERS
    for (ODatabaseListener listener : browseListeners())
      try {
        listener.onBeforeTxBegin(this);
      } catch (Throwable t) {
        OLogManager.instance().error(this, "Error before the transaction begin", t, OTransactionBlockedException.class);
      }

    currentTx = iTx;
    currentTx.begin();

    return this;
  }

  /**
   * {@inheritDoc}
   */
  public <RET extends ORecord> RET load(final ORecord iRecord, final String iFetchPlan, final boolean iIgnoreCache) {
    return (RET) executeReadRecord((ORecordId) iRecord.getIdentity(), iRecord, iFetchPlan, iIgnoreCache, false,
        OStorage.LOCKING_STRATEGY.DEFAULT);
  }

  /**
   * This method is internal, it can be subject to signature change or be removed, do not use.
   * 
   * @Internal
   */
  public <RET extends ORecord> RET executeReadRecord(final ORecordId rid, ORecord iRecord, final String iFetchPlan,
      final boolean iIgnoreCache, final boolean loadTombstones, final OStorage.LOCKING_STRATEGY iLockingStrategy) {
    checkOpeness();

    ((OMetadataInternal) getMetadata()).makeThreadLocalSchemaSnapshot();
    ORecordSerializationContext.pushContext();
    try {
      checkSecurity(ORule.ResourceGeneric.CLUSTER, ORole.PERMISSION_READ, getClusterNameById(rid.getClusterId()));

      // SEARCH IN LOCAL TX
      ORecord record = getTransaction().getRecord(rid);
      if (record == OTransactionRealAbstract.DELETED_RECORD)
        // DELETED IN TX
        return null;

      if (record == null && !iIgnoreCache)
        // SEARCH INTO THE CACHE
        record = getLocalCache().findRecord(rid);

      if (record != null) {
        if (iRecord != null) {
          iRecord.fromStream(record.toStream());
          iRecord.getRecordVersion().copyFrom(record.getRecordVersion());
          record = iRecord;
        }

        OFetchHelper.checkFetchPlanValid(iFetchPlan);
        if (callbackHooks(ORecordHook.TYPE.BEFORE_READ, record) == ORecordHook.RESULT.SKIP)
          return null;

        if (record.getInternalStatus() == ORecordElement.STATUS.NOT_LOADED)
          record.reload();

        if (iLockingStrategy == OStorage.LOCKING_STRATEGY.KEEP_SHARED_LOCK)
          record.lock(false);
        else if (iLockingStrategy == OStorage.LOCKING_STRATEGY.KEEP_EXCLUSIVE_LOCK)
          record.lock(true);

        callbackHooks(ORecordHook.TYPE.AFTER_READ, record);
        return (RET) record;
      }

      final ORawBuffer recordBuffer;
      if (!rid.isValid())
        recordBuffer = null;
      else {
        OFetchHelper.checkFetchPlanValid(iFetchPlan);
        recordBuffer = storage.readRecord(rid, iFetchPlan, iIgnoreCache, null, loadTombstones, iLockingStrategy).getResult();
      }

      if (recordBuffer == null)
        return null;

      if (iRecord == null || ORecordInternal.getRecordType(iRecord) != recordBuffer.recordType)
        // NO SAME RECORD TYPE: CAN'T REUSE OLD ONE BUT CREATE A NEW ONE FOR IT
        iRecord = Orient.instance().getRecordFactoryManager().newInstance(recordBuffer.recordType);

      ORecordInternal.fill(iRecord, rid, recordBuffer.version, recordBuffer.buffer, false);

      if (iRecord.getRecordVersion().isTombstone())
        return (RET) iRecord;

      if (callbackHooks(ORecordHook.TYPE.BEFORE_READ, iRecord) == ORecordHook.RESULT.SKIP)
        return null;

      iRecord.fromStream(recordBuffer.buffer);

      callbackHooks(ORecordHook.TYPE.AFTER_READ, iRecord);

      if (!iIgnoreCache)
        getLocalCache().updateRecord(iRecord);

      return (RET) iRecord;
    } catch (OOfflineClusterException t) {
      throw t;
    } catch (Throwable t) {
      if (rid.isTemporary())
        throw new ODatabaseException("Error on retrieving record using temporary RecordId: " + rid, t);
      else
        throw new ODatabaseException("Error on retrieving record " + rid + " (cluster: "
            + storage.getPhysicalClusterNameById(rid.clusterId) + ")", t);
    } finally {
      ORecordSerializationContext.pullContext();
      ((OMetadataInternal) getMetadata()).clearThreadLocalSchemaSnapshot();
    }
  }

  /**
   * This method is internal, it can be subject to signature change or be removed, do not use.
   * 
   * @Internal
   */
  public <RET extends ORecord> RET executeSaveRecord(final ORecord record, String clusterName, final ORecordVersion ver,
      boolean callTriggers, final OPERATION_MODE mode, boolean forceCreate,
      final ORecordCallback<? extends Number> recordCreatedCallback, ORecordCallback<ORecordVersion> recordUpdatedCallback) {
    checkOpeness();
    setCurrentDatabaseInThreadLocal();

    if (!record.isDirty())
      return (RET) record;

    final ORecordId rid = (ORecordId) record.getIdentity();

    if (rid == null)
      throw new ODatabaseException(
          "Cannot create record because it has no identity. Probably is not a regular record or contains projections of fields rather than a full record");

    final Set<OIndex<?>> lockedIndexes = new HashSet<OIndex<?>>();
    record.setInternalStatus(ORecordElement.STATUS.MARSHALLING);
    try {
      if (record instanceof ODocument)
        acquireIndexModificationLock((ODocument) record, lockedIndexes);

      final boolean wasNew = forceCreate || rid.isNew();

      if (wasNew && rid.clusterId == -1 && (clusterName != null || storage.isAssigningClusterIds())) {
        // ASSIGN THE CLUSTER ID
        if (clusterName != null)
          rid.clusterId = getClusterIdByName(clusterName);
        else if (record instanceof ODocument && ODocumentInternal.getImmutableSchemaClass(((ODocument) record)) != null)
          rid.clusterId = ODocumentInternal.getImmutableSchemaClass(((ODocument) record)).getClusterForNewInstance(
              (ODocument) record);
        else
          getDefaultClusterId();
      }

      byte[] stream = null;
      final OStorageOperationResult<ORecordVersion> operationResult;

      ((OMetadataInternal) getMetadata()).makeThreadLocalSchemaSnapshot();
      ORecordSerializationContext.pushContext();
      try {
        // STREAM.LENGTH == 0 -> RECORD IN STACK: WILL BE SAVED AFTER
        stream = record.toStream();

        final boolean isNew = forceCreate || rid.isNew();
        if (isNew)
          // NOTIFY IDENTITY HAS CHANGED
          ORecordInternal.onBeforeIdentityChanged(record);
        else if (stream == null || stream.length == 0)
          // ALREADY CREATED AND WAITING FOR THE RIGHT UPDATE (WE'RE IN A TREE/GRAPH)
          return (RET) record;

        if (isNew && rid.clusterId < 0 && storage.isAssigningClusterIds())
          rid.clusterId = clusterName != null ? getClusterIdByName(clusterName) : getDefaultClusterId();

        if (rid.clusterId > -1 && clusterName == null)
          clusterName = getClusterNameById(rid.clusterId);

        if (storage.isAssigningClusterIds())
          checkRecordClass(record, clusterName, rid, isNew);

        checkSecurity(ORule.ResourceGeneric.CLUSTER, wasNew ? ORole.PERMISSION_CREATE : ORole.PERMISSION_UPDATE, clusterName);

        final boolean partialMarshalling = record instanceof ODocument
            && OSerializationSetThreadLocal.INSTANCE.checkIfPartial((ODocument) record);

        if (stream != null && stream.length > 0 && !partialMarshalling) {
          if (callTriggers) {
            final ORecordHook.TYPE triggerType = wasNew ? ORecordHook.TYPE.BEFORE_CREATE : ORecordHook.TYPE.BEFORE_UPDATE;

            final ORecordHook.RESULT hookResult = callbackHooks(triggerType, record);

            if (hookResult == ORecordHook.RESULT.RECORD_CHANGED)
              stream = updateStream(record);
            else if (hookResult == ORecordHook.RESULT.SKIP_IO)
              return (RET) record;
            else if (hookResult == ORecordHook.RESULT.RECORD_REPLACED)
              // RETURNED THE REPLACED RECORD
              return (RET) OHookReplacedRecordThreadLocal.INSTANCE.get();
          }
        }

        if (wasNew && !isNew)
          // UPDATE RECORD PREVIOUSLY SERIALIZED AS EMPTY
          record.setDirty();
        else if (!record.isDirty())
          return (RET) record;

        try {
          // SAVE IT
          boolean updateContent = ORecordInternal.isContentChanged(record);
          byte[] content = (stream == null) ? new byte[0] : stream;
          byte recordType = ORecordInternal.getRecordType(record);
          final int modeIndex = mode.ordinal();

          // CHECK IF RECORD TYPE IS SUPPORTED
          Orient.instance().getRecordFactoryManager().getRecordTypeClass(recordType);

          if (forceCreate || ORecordId.isNew(rid.clusterPosition)) {
            // CREATE
            final OStorageOperationResult<OPhysicalPosition> ppos = storage.createRecord(rid, content, ver, recordType, modeIndex,
                (ORecordCallback<Long>) recordCreatedCallback);
            operationResult = new OStorageOperationResult<ORecordVersion>(ppos.getResult().recordVersion, ppos.isMoved());

          } else {
            // UPDATE
            operationResult = storage.updateRecord(rid, updateContent, content, ver, recordType, modeIndex, recordUpdatedCallback);
          }

          final ORecordVersion version = operationResult.getResult();

          if (isNew) {
            // UPDATE INFORMATION: CLUSTER ID+POSITION
            ((ORecordId) record.getIdentity()).copyFrom(rid);
            // NOTIFY IDENTITY HAS CHANGED
            ORecordInternal.onAfterIdentityChanged(record);
            // UPDATE INFORMATION: CLUSTER ID+POSITION
          }

          if (operationResult.getModifiedRecordContent() != null)
            stream = operationResult.getModifiedRecordContent();

          ORecordInternal.fill(record, rid, version, stream, partialMarshalling);

          callbackHookSuccess(record, callTriggers, wasNew, stream, operationResult);
        } catch (Throwable t) {
          callbackHookFailure(record, callTriggers, wasNew, stream);
          throw t;
        }
      } finally {
        callbackHookFinalize(record, callTriggers, wasNew, stream);
        ORecordSerializationContext.pullContext();
        ((OMetadataInternal) getMetadata()).clearThreadLocalSchemaSnapshot();
      }

      if (stream != null && stream.length > 0 && !operationResult.isMoved())
        // ADD/UPDATE IT IN CACHE IF IT'S ACTIVE
        getLocalCache().updateRecord(record);
    } catch (OException e) {
      throw e;
    } catch (Throwable t) {
      if (!ORecordId.isValid(record.getIdentity().getClusterPosition()))
        throw new ODatabaseException("Error on saving record in cluster #" + record.getIdentity().getClusterId(), t);
      else
        throw new ODatabaseException("Error on saving record " + record.getIdentity(), t);

    } finally {
      releaseIndexModificationLock(lockedIndexes);
      record.setInternalStatus(ORecordElement.STATUS.LOADED);
    }
    return (RET) record;
  }

  /**
   * This method is internal, it can be subject to signature change or be removed, do not use.
   * 
   * @Internal
   */
  public void executeDeleteRecord(OIdentifiable record, final ORecordVersion iVersion, final boolean iRequired,
      boolean iCallTriggers, final OPERATION_MODE iMode, boolean prohibitTombstones) {
    checkOpeness();
    final ORecordId rid = (ORecordId) record.getIdentity();

    if (rid == null)
      throw new ODatabaseException(
          "Cannot delete record because it has no identity. Probably was created from scratch or contains projections of fields rather than a full record");

    if (!rid.isValid())
      return;

    record = record.getRecord();
    if (record == null)
      return;

    checkSecurity(ORule.ResourceGeneric.CLUSTER, ORole.PERMISSION_DELETE, getClusterNameById(rid.clusterId));

    final Set<OIndex<?>> lockedIndexes = new HashSet<OIndex<?>>();
    setCurrentDatabaseInThreadLocal();

    ORecordSerializationContext.pushContext();
    ((OMetadataInternal) getMetadata()).makeThreadLocalSchemaSnapshot();
    try {
      if (record instanceof ODocument)
        acquireIndexModificationLock((ODocument) record, lockedIndexes);

      try {
        // if cache is switched off record will be unreachable after delete.
        ORecord rec = record.getRecord();
        if (iCallTriggers && rec != null)
          callbackHooks(ORecordHook.TYPE.BEFORE_DELETE, rec);

        // CHECK IF ENABLE THE MVCC OR BYPASS IT
        final ORecordVersion realVersion = mvcc ? iVersion : OVersionFactory.instance().createUntrackedVersion();

        final OStorageOperationResult<Boolean> operationResult;
        try {
          if (prohibitTombstones) {
            final boolean result = storage.cleanOutRecord(rid, iVersion, iMode.ordinal(), null);
            if (!result && iRequired)
              throw new ORecordNotFoundException("The record with id " + rid + " was not found");
            operationResult = new OStorageOperationResult<Boolean>(result);
          } else {
            final OStorageOperationResult<Boolean> result = storage.deleteRecord(rid, iVersion, iMode.ordinal(), null);
            if (!result.getResult() && iRequired)
              throw new ORecordNotFoundException("The record with id " + rid + " was not found");
            operationResult = new OStorageOperationResult<Boolean>(result.getResult());
          }

          if (iCallTriggers) {
            if (!operationResult.isMoved() && rec != null)
              callbackHooks(ORecordHook.TYPE.AFTER_DELETE, rec);
            else if (rec != null)
              callbackHooks(ORecordHook.TYPE.DELETE_REPLICATED, rec);
          }
        } catch (Throwable t) {
          if (iCallTriggers)
            callbackHooks(ORecordHook.TYPE.DELETE_FAILED, rec);
          throw t;
        }

        clearDocumentTracking(rec);

        // REMOVE THE RECORD FROM 1 AND 2 LEVEL CACHES
        if (!operationResult.isMoved()) {
          getLocalCache().deleteRecord(rid);
        }

      } catch (OException e) {
        // RE-THROW THE EXCEPTION
        throw e;

      } catch (Throwable t) {
        // WRAP IT AS ODATABASE EXCEPTION
        throw new ODatabaseException("Error on deleting record in cluster #" + record.getIdentity().getClusterId(), t);
      }
    } finally {
      releaseIndexModificationLock(lockedIndexes);
      ORecordSerializationContext.pullContext();
      ((OMetadataInternal) getMetadata()).clearThreadLocalSchemaSnapshot();
    }
  }

  /**
   * This method is internal, it can be subject to signature change or be removed, do not use.
   * 
   * @Internal
   */
  public boolean executeHideRecord(OIdentifiable record, final OPERATION_MODE iMode) {
    checkOpeness();
    final ORecordId rid = (ORecordId) record.getIdentity();

    if (rid == null)
      throw new ODatabaseException(
          "Cannot hide record because it has no identity. Probably was created from scratch or contains projections of fields rather than a full record");

    if (!rid.isValid())
      return false;

    checkSecurity(ORule.ResourceGeneric.CLUSTER, ORole.PERMISSION_DELETE, getClusterNameById(rid.clusterId));

    setCurrentDatabaseInThreadLocal();
    ((OMetadataInternal) getMetadata()).makeThreadLocalSchemaSnapshot();
    ORecordSerializationContext.pushContext();
    try {

      final OStorageOperationResult<Boolean> operationResult;
      operationResult = storage.hideRecord(rid, iMode.ordinal(), null);

      // REMOVE THE RECORD FROM 1 AND 2 LEVEL CACHES
      if (!operationResult.isMoved())
        getLocalCache().deleteRecord(rid);

      return operationResult.getResult();
    } finally {
      ORecordSerializationContext.pullContext();
      ((OMetadataInternal) getMetadata()).clearThreadLocalSchemaSnapshot();
    }
  }

  public ODatabaseDocumentTx begin() {
    return begin(OTransaction.TXTYPE.OPTIMISTIC);
  }

  public ODatabaseDocumentTx begin(final OTransaction.TXTYPE iType) {
    checkOpeness();
    setCurrentDatabaseInThreadLocal();

    if (currentTx.isActive()) {
      if (iType == OTransaction.TXTYPE.OPTIMISTIC && currentTx instanceof OTransactionOptimistic) {
        currentTx.begin();
        return this;
      }

      currentTx.rollback(true, 0);
    }

    // WAKE UP LISTENERS
    for (ODatabaseListener listener : browseListeners())
      try {
        listener.onBeforeTxBegin(this);
      } catch (Throwable t) {
        OLogManager.instance().error(this, "Error before tx begin", t);
      }

    switch (iType) {
    case NOTX:
      setDefaultTransactionMode();
      break;

    case OPTIMISTIC:
      currentTx = new OTransactionOptimistic(this);
      break;

    case PESSIMISTIC:
      throw new UnsupportedOperationException("Pessimistic transaction");
    }

    currentTx.begin();
    return this;
  }

  public void setDefaultTransactionMode() {
    if (!(currentTx instanceof OTransactionNoTx))
      currentTx = new OTransactionNoTx(this);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void freeze(final boolean throwException) {
    checkOpeness();
    if (!(getStorage() instanceof OFreezableStorage)) {
      OLogManager.instance().error(this,
          "We can not freeze non local storage. " + "If you use remote client please use OServerAdmin instead.");

      return;
    }

    final long startTime = Orient.instance().getProfiler().startChrono();

    final Collection<? extends OIndex<?>> indexes = getMetadata().getIndexManager().getIndexes();
    final List<OIndexAbstract<?>> indexesToLock = prepareIndexesToFreeze(indexes);

    freezeIndexes(indexesToLock, true);
    flushIndexes(indexesToLock);

    final OFreezableStorage storage = getFreezableStorage();
    if (storage != null) {
      storage.freeze(throwException);
    }

    Orient.instance().getProfiler()
        .stopChrono("db." + getName() + ".freeze", "Time to freeze the database", startTime, "db.*.freeze");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void freeze() {
    checkOpeness();
    if (!(getStorage() instanceof OFreezableStorage)) {
      OLogManager.instance().error(this,
          "We can not freeze non local storage. " + "If you use remote client please use OServerAdmin instead.");

      return;
    }

    final long startTime = Orient.instance().getProfiler().startChrono();

    final Collection<? extends OIndex<?>> indexes = getMetadata().getIndexManager().getIndexes();
    final List<OIndexAbstract<?>> indexesToLock = prepareIndexesToFreeze(indexes);

    freezeIndexes(indexesToLock, false);
    flushIndexes(indexesToLock);

    final OFreezableStorage storage = getFreezableStorage();
    if (storage != null) {
      storage.freeze(false);
    }

    Orient.instance().getProfiler()
        .stopChrono("db." + getName() + ".freeze", "Time to freeze the database", startTime, "db.*.freeze");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void release() {
    checkOpeness();
    if (!(getStorage() instanceof OFreezableStorage)) {
      OLogManager.instance().error(this,
          "We can not release non local storage. " + "If you use remote client please use OServerAdmin instead.");
      return;
    }

    final long startTime = Orient.instance().getProfiler().startChrono();

    final OFreezableStorage storage = getFreezableStorage();
    if (storage != null) {
      storage.release();
    }

    Collection<? extends OIndex<?>> indexes = getMetadata().getIndexManager().getIndexes();
    releaseIndexes(indexes);

    Orient.instance().getProfiler()
        .stopChrono("db." + getName() + ".release", "Time to release the database", startTime, "db.*.release");
  }

  /**
   * Creates a new ODocument.
   */
  public ODocument newInstance() {
    return new ODocument();
  }

  /**
   * Creates a document with specific class.
   * 
   * @param iClassName
   *          the name of class that should be used as a class of created document.
   * @return new instance of document.
   */
  @Override
  public ODocument newInstance(final String iClassName) {
    return new ODocument(iClassName);
  }

  /**
   * {@inheritDoc}
   */
  public ORecordIteratorClass<ODocument> browseClass(final String iClassName) {
    return browseClass(iClassName, true);
  }

  /**
   * {@inheritDoc}
   */
  public ORecordIteratorClass<ODocument> browseClass(final String iClassName, final boolean iPolymorphic) {
    if (((OMetadataInternal) getMetadata()).getImmutableSchemaSnapshot().getClass(iClassName) == null)
      throw new IllegalArgumentException("Class '" + iClassName + "' not found in current database");

    checkSecurity(ORule.ResourceGeneric.CLASS, ORole.PERMISSION_READ, iClassName);

    return new ORecordIteratorClass<ODocument>(this, this, iClassName, iPolymorphic, true, false);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ORecordIteratorCluster<ODocument> browseCluster(final String iClusterName) {
    checkSecurity(ORule.ResourceGeneric.CLUSTER, ORole.PERMISSION_READ, iClusterName);

    return new ORecordIteratorCluster<ODocument>(this, this, getClusterIdByName(iClusterName), true);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ORecordIteratorCluster<ODocument> browseCluster(String iClusterName, long startClusterPosition, long endClusterPosition,
      boolean loadTombstones) {
    checkSecurity(ORule.ResourceGeneric.CLUSTER, ORole.PERMISSION_READ, iClusterName);

    return new ORecordIteratorCluster<ODocument>(this, this, getClusterIdByName(iClusterName), startClusterPosition,
        endClusterPosition, true, loadTombstones, OStorage.LOCKING_STRATEGY.DEFAULT);
  }

  /**
   * Saves a document to the database. Behavior depends by the current running transaction if any. If no transaction is running then
   * changes apply immediately. If an Optimistic transaction is running then the record will be changed at commit time. The current
   * transaction will continue to see the record as modified, while others not. If a Pessimistic transaction is running, then an
   * exclusive lock is acquired against the record. Current transaction will continue to see the record as modified, while others
   * cannot access to it since it's locked.
   * <p/>
   * If MVCC is enabled and the version of the document is different by the version stored in the database, then a
   * {@link OConcurrentModificationException} exception is thrown.Before to save the document it must be valid following the
   * constraints declared in the schema if any (can work also in schema-less mode). To validate the document the
   * {@link ODocument#validate()} is called.
   * 
   * @param iRecord
   *          Record to save.
   * @return The Database instance itself giving a "fluent interface". Useful to call multiple methods in chain.
   * @throws OConcurrentModificationException
   *           if the version of the document is different by the version contained in the database.
   * @throws OValidationException
   *           if the document breaks some validation constraints defined in the schema
   * @see #setMVCC(boolean), {@link #isMVCC()}
   */
  @Override
  public <RET extends ORecord> RET save(final ORecord iRecord) {
    return (RET) save(iRecord, null, OPERATION_MODE.SYNCHRONOUS, false, null, null);
  }

  /**
   * Saves a document to the database. Behavior depends by the current running transaction if any. If no transaction is running then
   * changes apply immediately. If an Optimistic transaction is running then the record will be changed at commit time. The current
   * transaction will continue to see the record as modified, while others not. If a Pessimistic transaction is running, then an
   * exclusive lock is acquired against the record. Current transaction will continue to see the record as modified, while others
   * cannot access to it since it's locked.
   * <p/>
   * If MVCC is enabled and the version of the document is different by the version stored in the database, then a
   * {@link OConcurrentModificationException} exception is thrown.Before to save the document it must be valid following the
   * constraints declared in the schema if any (can work also in schema-less mode). To validate the document the
   * {@link ODocument#validate()} is called.
   * 
   * 
   * 
   * @param iRecord
   *          Record to save.
   * @param iForceCreate
   *          Flag that indicates that record should be created. If record with current rid already exists, exception is thrown
   * @param iRecordCreatedCallback
   *          callback that is called after creation of new record
   * @param iRecordUpdatedCallback
   *          callback that is called after record update
   * @return The Database instance itself giving a "fluent interface". Useful to call multiple methods in chain.
   * @throws OConcurrentModificationException
   *           if the version of the document is different by the version contained in the database.
   * @throws OValidationException
   *           if the document breaks some validation constraints defined in the schema
   * @see #setMVCC(boolean), {@link #isMVCC()}
   */
  @Override
  public <RET extends ORecord> RET save(final ORecord iRecord, final OPERATION_MODE iMode, boolean iForceCreate,
      final ORecordCallback<? extends Number> iRecordCreatedCallback, ORecordCallback<ORecordVersion> iRecordUpdatedCallback) {
    return save(iRecord, null, iMode, iForceCreate, iRecordCreatedCallback, iRecordUpdatedCallback);
  }

  /**
   * Saves a document specifying a cluster where to store the record. Behavior depends by the current running transaction if any. If
   * no transaction is running then changes apply immediately. If an Optimistic transaction is running then the record will be
   * changed at commit time. The current transaction will continue to see the record as modified, while others not. If a Pessimistic
   * transaction is running, then an exclusive lock is acquired against the record. Current transaction will continue to see the
   * record as modified, while others cannot access to it since it's locked.
   * <p/>
   * If MVCC is enabled and the version of the document is different by the version stored in the database, then a
   * {@link OConcurrentModificationException} exception is thrown. Before to save the document it must be valid following the
   * constraints declared in the schema if any (can work also in schema-less mode). To validate the document the
   * {@link ODocument#validate()} is called.
   * 
   * @param iRecord
   *          Record to save
   * @param iClusterName
   *          Cluster name where to save the record
   * @return The Database instance itself giving a "fluent interface". Useful to call multiple methods in chain.
   * @throws OConcurrentModificationException
   *           if the version of the document is different by the version contained in the database.
   * @throws OValidationException
   *           if the document breaks some validation constraints defined in the schema
   * @see #setMVCC(boolean), {@link #isMVCC()}, ODocument#validate()
   */
  @Override
  public <RET extends ORecord> RET save(final ORecord iRecord, final String iClusterName) {
    return (RET) save(iRecord, iClusterName, OPERATION_MODE.SYNCHRONOUS, false, null, null);
  }

  /**
   * Saves a document specifying a cluster where to store the record. Behavior depends by the current running transaction if any. If
   * no transaction is running then changes apply immediately. If an Optimistic transaction is running then the record will be
   * changed at commit time. The current transaction will continue to see the record as modified, while others not. If a Pessimistic
   * transaction is running, then an exclusive lock is acquired against the record. Current transaction will continue to see the
   * record as modified, while others cannot access to it since it's locked.
   * <p/>
   * If MVCC is enabled and the version of the document is different by the version stored in the database, then a
   * {@link OConcurrentModificationException} exception is thrown. Before to save the document it must be valid following the
   * constraints declared in the schema if any (can work also in schema-less mode). To validate the document the
   * {@link ODocument#validate()} is called.
   * 
   * 
   * @param iRecord
   *          Record to save
   * @param iClusterName
   *          Cluster name where to save the record
   * @param iMode
   *          Mode of save: synchronous (default) or asynchronous
   * @param iForceCreate
   *          Flag that indicates that record should be created. If record with current rid already exists, exception is thrown
   * @param iRecordCreatedCallback
   *          callback that is called after creation of new record
   * @param iRecordUpdatedCallback
   *          callback that is called after record update
   * @return The Database instance itself giving a "fluent interface". Useful to call multiple methods in chain.
   * @throws OConcurrentModificationException
   *           if the version of the document is different by the version contained in the database.
   * @throws OValidationException
   *           if the document breaks some validation constraints defined in the schema
   * @see #setMVCC(boolean), {@link #isMVCC()}, ODocument#validate()
   */
  @Override
  public <RET extends ORecord> RET save(final ORecord iRecord, String iClusterName, final OPERATION_MODE iMode,
      boolean iForceCreate, final ORecordCallback<? extends Number> iRecordCreatedCallback,
      ORecordCallback<ORecordVersion> iRecordUpdatedCallback) {
    checkOpeness();
    if (!(iRecord instanceof ODocument))
      return (RET) currentTx.saveRecord(iRecord, iClusterName, iMode, iForceCreate, iRecordCreatedCallback, iRecordUpdatedCallback);

    ODocument doc = (ODocument) iRecord;
    doc.validate();
    ODocumentInternal.convertAllMultiValuesToTrackedVersions(doc);

    if (iForceCreate || !doc.getIdentity().isValid()) {
      if (doc.getClassName() != null)
        checkSecurity(ORule.ResourceGeneric.CLASS, ORole.PERMISSION_CREATE, doc.getClassName());

      final OClass schemaClass = ODocumentInternal.getImmutableSchemaClass(doc);

      int clusterId = iRecord.getIdentity().getClusterId();
      if (clusterId == ORID.CLUSTER_ID_INVALID) {
        // COMPUTE THE CLUSTER ID
        if (iClusterName == null) {
          if (storage.isAssigningClusterIds()) {
            if (schemaClass != null) {
              // FIND THE RIGHT CLUSTER AS CONFIGURED IN CLASS
              if (schemaClass.isAbstract())
                throw new OSchemaException("Document belongs to abstract class " + schemaClass.getName() + " and can not be saved");

              iClusterName = getClusterNameById(schemaClass.getClusterForNewInstance(doc));
            } else {
              iClusterName = getClusterNameById(storage.getDefaultClusterId());
            }
          }
        }

        if (iClusterName != null) {
          clusterId = getClusterIdByName(iClusterName);
          if (clusterId == -1)
            throw new IllegalArgumentException("Cluster name '" + iClusterName + "' is not configured");
        }
      }

      final int[] clusterIds;
      if (schemaClass != null && clusterId > -1) {
        // CHECK IF THE CLUSTER IS PART OF THE CONFIGURED CLUSTERS
        clusterIds = schemaClass.getClusterIds();
        int i = 0;
        for (; i < clusterIds.length; ++i)
          if (clusterIds[i] == clusterId)
            break;

        if (i == clusterIds.length)
          throw new IllegalArgumentException("Cluster name '" + iClusterName + "' (id=" + clusterId
              + ") is not configured to store the class '" + doc.getClassName() + "', valid are " + Arrays.toString(clusterIds));
      }

      // SET BACK THE CLUSTER ID
      ((ORecordId) iRecord.getIdentity()).clusterId = clusterId;

    } else {
      // UPDATE: CHECK ACCESS ON SCHEMA CLASS NAME (IF ANY)
      if (doc.getClassName() != null)
        checkSecurity(ORule.ResourceGeneric.CLASS, ORole.PERMISSION_UPDATE, doc.getClassName());
    }

    doc = (ODocument) currentTx.saveRecord(iRecord, iClusterName, iMode, iForceCreate, iRecordCreatedCallback,
        iRecordUpdatedCallback);
    return (RET) doc;
  }

  /**
   * Deletes a document. Behavior depends by the current running transaction if any. If no transaction is running then the record is
   * deleted immediately. If an Optimistic transaction is running then the record will be deleted at commit time. The current
   * transaction will continue to see the record as deleted, while others not. If a Pessimistic transaction is running, then an
   * exclusive lock is acquired against the record. Current transaction will continue to see the record as deleted, while others
   * cannot access to it since it's locked.
   * <p/>
   * If MVCC is enabled and the version of the document is different by the version stored in the database, then a
   * {@link OConcurrentModificationException} exception is thrown.
   * 
   * @param record
   *          record to delete
   * @return The Database instance itself giving a "fluent interface". Useful to call multiple methods in chain.
   * @see #setMVCC(boolean), {@link #isMVCC()}
   */
  public ODatabaseDocumentTx delete(final ORecord record) {
    checkOpeness();
    if (record == null)
      throw new ODatabaseException("Cannot delete null document");

    // CHECK ACCESS ON SCHEMA CLASS NAME (IF ANY)
    if (record instanceof ODocument && ((ODocument) record).getClassName() != null)
      checkSecurity(ORule.ResourceGeneric.CLASS, ORole.PERMISSION_DELETE, ((ODocument) record).getClassName());

    try {
      currentTx.deleteRecord(record, OPERATION_MODE.SYNCHRONOUS);
    } catch (OException e) {
      throw e;
    } catch (Exception e) {
      if (record instanceof ODocument)
        throw new ODatabaseException("Error on deleting record " + record.getIdentity() + " of class '"
            + ((ODocument) record).getClassName() + "'", e);
      else
        throw new ODatabaseException("Error on deleting record " + record.getIdentity());
    }
    return this;
  }

  /**
   * Returns the number of the records of the class iClassName.
   */
  public long countClass(final String iClassName) {
    return countClass(iClassName, true);
  }

  /**
   * Returns the number of the records of the class iClassName considering also sub classes if polymorphic is true.
   */
  public long countClass(final String iClassName, final boolean iPolymorphic) {
    ODatabaseRecordThreadLocal.INSTANCE.set(this);

    final OClass cls = ((OMetadataInternal) getMetadata()).getImmutableSchemaSnapshot().getClass(iClassName);

    if (cls == null)
      throw new IllegalArgumentException("Class '" + iClassName + "' not found in database");

    return cls.count(iPolymorphic);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ODatabase<ORecord> commit() {
    return commit(false);
  }

  @Override
  public ODatabaseDocument commit(boolean force) throws OTransactionException {
    checkOpeness();
    if (!currentTx.isActive())
      return this;

    if (!force && currentTx.amountOfNestedTxs() > 1) {
      currentTx.commit();
      return this;
    }

    setCurrentDatabaseInThreadLocal();
    // WAKE UP LISTENERS
    for (ODatabaseListener listener : browseListeners())
      try {
        listener.onBeforeTxCommit(this);
      } catch (Throwable t) {
        try {
          rollback(force);
        } catch (RuntimeException e) {
          throw e;
        }
        OLogManager.instance().debug(this, "Cannot commit the transaction: caught exception on execution of %s.onBeforeTxCommit()",
            t, OTransactionBlockedException.class, listener.getClass());
      }

    try {
      currentTx.commit(force);
    } catch (RuntimeException e) {
      // WAKE UP ROLLBACK LISTENERS
      for (ODatabaseListener listener : browseListeners())
        try {
          listener.onBeforeTxRollback(this);
        } catch (Throwable t) {
          OLogManager.instance().error(this, "Error before tx rollback", t);
        }
      // ROLLBACK TX AT DB LEVEL
      currentTx.rollback(false, 0);
      getLocalCache().clear();

      // WAKE UP ROLLBACK LISTENERS
      for (ODatabaseListener listener : browseListeners())
        try {
          listener.onAfterTxRollback(this);
        } catch (Throwable t) {
          OLogManager.instance().error(this, "Error after tx rollback", t);
        }
      throw e;
    }

    // WAKE UP LISTENERS
    for (ODatabaseListener listener : browseListeners())
      try {
        listener.onAfterTxCommit(this);
      } catch (Throwable t) {
        OLogManager
            .instance()
            .debug(
                this,
                "Error after the transaction has been committed. The transaction remains valid. The exception caught was on execution of %s.onAfterTxCommit()",
                t, OTransactionBlockedException.class, listener.getClass());
      }

    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ODatabase<ORecord> rollback() {
    return rollback(false);
  }

  @Override
  public ODatabaseDocument rollback(boolean force) throws OTransactionException {
    checkOpeness();
    if (currentTx.isActive()) {

      if (!force && currentTx.amountOfNestedTxs() > 1) {
        currentTx.rollback();
        return this;
      }

      // WAKE UP LISTENERS
      for (ODatabaseListener listener : browseListeners())
        try {
          listener.onBeforeTxRollback(this);
        } catch (Throwable t) {
          OLogManager.instance().error(this, "Error before tx rollback", t);
        }

      currentTx.rollback(force, -1);

      // WAKE UP LISTENERS
      for (ODatabaseListener listener : browseListeners())
        try {
          listener.onAfterTxRollback(this);
        } catch (Throwable t) {
          OLogManager.instance().error(this, "Error after tx rollback", t);
        }
    }

    getLocalCache().clear();

    return this;
  }

  /**
   * This method is internal, it can be subject to signature change or be removed, do not use.
   * 
   * @Internal
   */
  @Override
  public <DB extends ODatabase> DB getUnderlying() {
    throw new UnsupportedOperationException();
  }

  /**
   * This method is internal, it can be subject to signature change or be removed, do not use.
   * 
   * @Internal
   */
  @Override
  public OStorage getStorage() {
    return storage;
  }

  /**
   * This method is internal, it can be subject to signature change or be removed, do not use.
   * 
   * @Internal
   */
  @Override
  public void replaceStorage(OStorage iNewStorage) {
    storage = iNewStorage;
  }

  @Override
  public <V> V callInLock(Callable<V> iCallable, boolean iExclusiveLock) {
    return storage.callInLock(iCallable, iExclusiveLock);
  }

  @Override
  public void backup(OutputStream out, Map<String, Object> options, Callable<Object> callable, OCommandOutputListener iListener,
      int compressionLevel, int bufferSize) throws IOException {
    storage.backup(out, options, callable, iListener, compressionLevel, bufferSize);
  }

  @Override
  public void restore(InputStream in, Map<String, Object> options, Callable<Object> callable, OCommandOutputListener iListener)
      throws IOException {
    if (storage == null)
      storage = Orient.instance().loadStorage(url);

    getStorage().restore(in, options, callable, iListener);
  }

  /**
   * {@inheritDoc}
   */
  public OSBTreeCollectionManager getSbTreeCollectionManager() {
    return sbTreeCollectionManager;
  }

  @Override
  public OCurrentStorageComponentsFactory getStorageVersions() {
    return componentsFactory;
  }

  public ORecordSerializer getSerializer() {
    return serializer;
  }

  /**
   * Sets serializer for the database which will be used for document serialization.
   * 
   * @param serializer
   *          the serializer to set.
   */
  public void setSerializer(ORecordSerializer serializer) {
    this.serializer = serializer;
  }

  @Override
  public void resetInitialization() {
    for (ORecordHook h : hooks.keySet())
      h.onUnregister();

    hooks.clear();

    close();

    initialized = false;
  }

  @Override
  @Deprecated
  public <DB extends ODatabaseDocument> DB checkSecurity(String iResource, int iOperation) {
    final String resourceSpecific = ORule.mapLegacyResourceToSpecificResource(iResource);
    final ORule.ResourceGeneric resourceGeneric = ORule.mapLegacyResourceToGenericResource(iResource);

    if (resourceSpecific == null || resourceSpecific.equals("*"))
      checkSecurity(resourceGeneric, null, iOperation);

    return checkSecurity(resourceGeneric, resourceSpecific, iOperation);
  }

  @Override
  @Deprecated
  public <DB extends ODatabaseDocument> DB checkSecurity(String iResourceGeneric, int iOperation, Object iResourceSpecific) {
    final ORule.ResourceGeneric resourceGeneric = ORule.mapLegacyResourceToGenericResource(iResourceGeneric);
    if (iResourceSpecific == null || iResourceSpecific.equals("*"))
      return checkSecurity(resourceGeneric, iOperation, (Object) null);

    return checkSecurity(resourceGeneric, iOperation, iResourceSpecific);
  }

  @Override
  @Deprecated
  public <DB extends ODatabaseDocument> DB checkSecurity(String iResourceGeneric, int iOperation, Object... iResourcesSpecific) {
    final ORule.ResourceGeneric resourceGeneric = ORule.mapLegacyResourceToGenericResource(iResourceGeneric);
    return checkSecurity(resourceGeneric, iOperation, iResourcesSpecific);
  }

  public void setCurrentDatabaseInThreadLocal() {
    ODatabaseRecordThreadLocal.INSTANCE.set(this);
  }

  protected void checkTransaction() {
    if (currentTx == null || currentTx.getStatus() == OTransaction.TXSTATUS.INVALID)
      throw new OTransactionException("Transaction not started");
  }

  @Deprecated
  protected ORecordSerializer resolveFormat(final Object iObject) {
    return ORecordSerializerFactory.instance().getFormatForObject(iObject, recordFormat);
  }

  protected void checkOpeness() {
    if (isClosed())
      throw new ODatabaseException("Database '" + getURL() + "' is closed");
  }

  private void initAtFirstOpen(String iUserName, String iUserPassword) {
    if (initialized)
      return;

    ORecordSerializerFactory serializerFactory = ORecordSerializerFactory.instance();
    String serializeName = getStorage().getConfiguration().getRecordSerializer();
    if (serializeName == null)
      serializeName = ORecordSerializerSchemaAware2CSV.NAME;
    serializer = serializerFactory.getFormat(serializeName);
    if (serializer == null)
      throw new ODatabaseException("RecordSerializer with name '" + serializeName + "' not found ");
    if (getStorage().getConfiguration().getRecordSerializerVersion() > serializer.getMinSupportedVersion())
      throw new ODatabaseException("Persistent record serializer version is not support by the current implementation");

    componentsFactory = getStorage().getComponentsFactory();

    final OSBTreeCollectionManager sbTreeCM = getStorage().getResource(OSBTreeCollectionManager.class.getSimpleName(),
        new Callable<OSBTreeCollectionManager>() {
          @Override
          public OSBTreeCollectionManager call() throws Exception {
            Class<? extends OSBTreeCollectionManager> managerClass = getStorage().getCollectionManagerClass();

            if (managerClass == null) {
              OLogManager.instance().warn(this, "Current implementation of storage does not support sbtree collections");
              return null;
            } else {
              return managerClass.newInstance();
            }
          }
        });

    sbTreeCollectionManager = sbTreeCM != null ? new OSBTreeCollectionManagerProxy(this, sbTreeCM) : null;

    localCache.startup();

    metadata = new OMetadataDefault();
    metadata.load();

    recordFormat = DEF_RECORD_FORMAT;

    if (!(getStorage() instanceof OStorageProxy)) {
      if (metadata.getIndexManager().autoRecreateIndexesAfterCrash()) {
        metadata.getIndexManager().recreateIndexes();

        setCurrentDatabaseInThreadLocal();
        user = null;
      }

      installHooks();
      registerHook(new OSecurityTrackerHook(metadata.getSecurity(), this), ORecordHook.HOOK_POSITION.LAST);

      user = null;
    } else if (iUserName != null && iUserPassword != null)
      user = new OImmutableUser(-1, new OUser(iUserName, OUser.encryptPassword(iUserPassword)).addRole(new ORole("passthrough",
          null, ORole.ALLOW_MODES.ALLOW_ALL_BUT)));

    if (ORecordSerializerSchemaAware2CSV.NAME.equals(serializeName)
        && !metadata.getSchema().existsClass(OMVRBTreeRIDProvider.PERSISTENT_CLASS_NAME))
      // @COMPATIBILITY 1.0RC9
      metadata.getSchema().createClass(OMVRBTreeRIDProvider.PERSISTENT_CLASS_NAME);

    if (OGlobalConfiguration.DB_MAKE_FULL_CHECKPOINT_ON_SCHEMA_CHANGE.getValueAsBoolean())
      metadata.getSchema().setFullCheckpointOnChange(true);

    if (OGlobalConfiguration.DB_MAKE_FULL_CHECKPOINT_ON_INDEX_CHANGE.getValueAsBoolean())
      metadata.getIndexManager().setFullCheckpointOnChange(true);

    initialized = true;
  }

  private void installHooks() {
    registerHook(new OClassTrigger(this), ORecordHook.HOOK_POSITION.FIRST);
    registerHook(new ORestrictedAccessHook(this), ORecordHook.HOOK_POSITION.FIRST);
    registerHook(new OUserTrigger(this), ORecordHook.HOOK_POSITION.EARLY);
    registerHook(new OFunctionTrigger(this), ORecordHook.HOOK_POSITION.REGULAR);
    registerHook(new OClassIndexManager(this), ORecordHook.HOOK_POSITION.LAST);
    registerHook(new OSchedulerTrigger(this), ORecordHook.HOOK_POSITION.LAST);
    registerHook(new ORidBagDeleteHook(this), ORecordHook.HOOK_POSITION.LAST);
  }

  private void closeOnDelete() {
    if (status != STATUS.OPEN)
      return;

    if (currentIntent != null) {
      currentIntent.end(this);
      currentIntent = null;
    }

    resetListeners();

    if (storage != null)
      storage.close(true, true);

    storage = null;
    status = STATUS.CLOSED;
  }

  private void clearCustomInternal() {
    storage.getConfiguration().properties.clear();
  }

  private void removeCustomInternal(final String iName) {
    setCustomInternal(iName, null);
  }

  private void setCustomInternal(final String iName, final String iValue) {
    if (iValue == null || "null".equalsIgnoreCase(iValue)) {
      // REMOVE
      for (Iterator<OStorageEntryConfiguration> it = storage.getConfiguration().properties.iterator(); it.hasNext();) {
        final OStorageEntryConfiguration e = it.next();
        if (e.name.equals(iName)) {
          it.remove();
          break;
        }
      }

    } else {
      // SET
      boolean found = false;
      for (OStorageEntryConfiguration e : storage.getConfiguration().properties) {
        if (e.name.equals(iName)) {
          e.value = iValue;
          found = true;
          break;
        }
      }

      if (!found)
        // CREATE A NEW ONE
        storage.getConfiguration().properties.add(new OStorageEntryConfiguration(iName, iValue));
    }

    storage.getConfiguration().update();
  }

  private void callbackHookFailure(ORecord record, boolean iCallTriggers, boolean wasNew, byte[] stream) {
    if (iCallTriggers && stream != null && stream.length > 0)
      callbackHooks(wasNew ? ORecordHook.TYPE.CREATE_FAILED : ORecordHook.TYPE.UPDATE_FAILED, record);
  }

  private void callbackHookSuccess(final ORecord record, final boolean iCallTriggers, final boolean wasNew, final byte[] stream,
      final OStorageOperationResult<ORecordVersion> operationResult) {
    if (iCallTriggers && stream != null && stream.length > 0) {
      final ORecordHook.TYPE hookType;
      if (!operationResult.isMoved()) {
        hookType = wasNew ? ORecordHook.TYPE.AFTER_CREATE : ORecordHook.TYPE.AFTER_UPDATE;
      } else {
        hookType = wasNew ? ORecordHook.TYPE.CREATE_REPLICATED : ORecordHook.TYPE.UPDATE_REPLICATED;
      }
      callbackHooks(hookType, record);

      clearDocumentTracking(record);
    }
  }

  private void callbackHookFinalize(final ORecord record, final boolean callTriggers, final boolean wasNew, final byte[] stream) {
    if (callTriggers && stream != null && stream.length > 0) {
      final ORecordHook.TYPE hookType;
      hookType = wasNew ? ORecordHook.TYPE.FINALIZE_CREATION : ORecordHook.TYPE.FINALIZE_UPDATE;
      callbackHooks(hookType, record);
    }
  }

  private void clearDocumentTracking(final ORecord record) {
    if (record instanceof ODocument && ((ODocument) record).isTrackingChanges()) {
      ((ODocument) record).setTrackingChanges(false);
      ((ODocument) record).setTrackingChanges(true);
    }
  }

  private void checkRecordClass(final ORecord record, final String iClusterName, final ORecordId rid, final boolean isNew) {
    if (rid.clusterId > -1 && getStorageVersions().classesAreDetectedByClusterId() && isNew && record instanceof ODocument) {
      final ODocument recordSchemaAware = (ODocument) record;
      final OClass recordClass = ODocumentInternal.getImmutableSchemaClass(recordSchemaAware);
      final OClass clusterIdClass = ((OMetadataInternal) metadata).getImmutableSchemaSnapshot().getClassByClusterId(rid.clusterId);
      if (recordClass == null && clusterIdClass != null || clusterIdClass == null && recordClass != null
          || (recordClass != null && !recordClass.equals(clusterIdClass)))
        throw new OSchemaException("Record saved into cluster '" + iClusterName + "' should be saved with class '" + clusterIdClass
            + "' but has been created with class '" + recordClass + "'");
    }
  }

  private byte[] updateStream(final ORecord record) {
    ORecordInternal.unsetDirty(record);
    record.setDirty();
    ORecordSerializationContext.pullContext();
    ORecordSerializationContext.pushContext();

    return record.toStream();
  }

  private void releaseIndexModificationLock(final Set<OIndex<?>> lockedIndexes) {
    if (metadata == null)
      return;

    final OIndexManager indexManager = metadata.getIndexManager();
    if (indexManager == null)
      return;

    for (OIndex<?> index : lockedIndexes) {
      index.getInternal().releaseModificationLock();
    }
  }

  private void acquireIndexModificationLock(final ODocument doc, final Set<OIndex<?>> lockedIndexes) {
    if (getStorage().getUnderlying() instanceof OAbstractPaginatedStorage) {
      final OClass cls = ODocumentInternal.getImmutableSchemaClass(doc);
      if (cls != null) {
        final Collection<OIndex<?>> indexes = cls.getIndexes();
        if (indexes != null) {
          final SortedSet<OIndex<?>> indexesToLock = new TreeSet<OIndex<?>>(new Comparator<OIndex<?>>() {
            public int compare(OIndex<?> indexOne, OIndex<?> indexTwo) {
              return indexOne.getName().compareTo(indexTwo.getName());
            }
          });

          indexesToLock.addAll(indexes);

          for (final OIndex<?> index : indexesToLock) {
            index.getInternal().acquireModificationLock();
            lockedIndexes.add(index);
          }
        }
      }
    }
  }

  private void init() {
    currentTx = new OTransactionNoTx(this);
  }

  private OFreezableStorage getFreezableStorage() {
    OStorage s = getStorage();
    if (s instanceof OFreezableStorage)
      return (OFreezableStorage) s;
    else {
      OLogManager.instance().error(this, "Storage of type " + s.getType() + " does not support freeze operation.");
      return null;
    }
  }

  private void freezeIndexes(final List<OIndexAbstract<?>> indexesToFreeze, final boolean throwException) {
    if (indexesToFreeze != null) {
      for (OIndexAbstract<?> indexToLock : indexesToFreeze) {
        indexToLock.freeze(throwException);
      }
    }
  }

  private void flushIndexes(final List<OIndexAbstract<?>> indexesToFlush) {
    for (OIndexAbstract<?> index : indexesToFlush) {
      index.flush();
    }
  }

  private List<OIndexAbstract<?>> prepareIndexesToFreeze(final Collection<? extends OIndex<?>> indexes) {
    List<OIndexAbstract<?>> indexesToFreeze = null;
    if (indexes != null && !indexes.isEmpty()) {
      indexesToFreeze = new ArrayList<OIndexAbstract<?>>(indexes.size());
      for (OIndex<?> index : indexes) {
        indexesToFreeze.add((OIndexAbstract<?>) index.getInternal());
      }

      Collections.sort(indexesToFreeze, new Comparator<OIndex<?>>() {
        public int compare(OIndex<?> o1, OIndex<?> o2) {
          return o1.getName().compareTo(o2.getName());
        }
      });

    }
    return indexesToFreeze;
  }

  private void releaseIndexes(final Collection<? extends OIndex<?>> indexesToRelease) {
    if (indexesToRelease != null) {
      Iterator<? extends OIndex<?>> it = indexesToRelease.iterator();
      while (it.hasNext()) {
        it.next().getInternal().release();
        it.remove();
      }
    }
  }
}
