package com.orientechnologies.orient.core.storage.impl.local.paginated.wal.po.cellbtree.singlevalue.v1.cellbtreebucketsinglevalue;

import com.orientechnologies.common.serialization.types.OByteSerializer;
import com.orientechnologies.orient.core.storage.cache.OCacheEntry;
import com.orientechnologies.orient.core.storage.impl.local.paginated.wal.WALRecordTypes;
import com.orientechnologies.orient.core.storage.impl.local.paginated.wal.po.PageOperationRecord;
import com.orientechnologies.orient.core.storage.index.sbtree.singlevalue.v1.OCellBTreeBucketSingleValue;

import java.nio.ByteBuffer;

public class CellBTreeBucketSingleValueInitPO extends PageOperationRecord {
  private boolean isLeaf;

  public CellBTreeBucketSingleValueInitPO() {
  }

  public CellBTreeBucketSingleValueInitPO(boolean isLeaf) {
    this.isLeaf = isLeaf;
  }

  @Override
  public void redo(OCacheEntry cacheEntry) {
    final OCellBTreeBucketSingleValue bucket = new OCellBTreeBucketSingleValue(cacheEntry);
    bucket.init(isLeaf);
  }

  @Override
  public void undo(OCacheEntry cacheEntry) {
  }

  @Override
  public byte getId() {
    return WALRecordTypes.CELL_BTREE_BUCKET_SINGLE_VALUE_INIT_PO;
  }

  @Override
  public int serializedSize() {
    return super.serializedSize() + OByteSerializer.BYTE_SIZE;
  }

  @Override
  protected void serializeToByteBuffer(ByteBuffer buffer) {
    super.serializeToByteBuffer(buffer);

    buffer.put(isLeaf ? (byte) 1 : 0);
  }

  @Override
  protected void deserializeFromByteBuffer(ByteBuffer buffer) {
    super.deserializeFromByteBuffer(buffer);

    isLeaf = buffer.get() > 0;
  }
}
