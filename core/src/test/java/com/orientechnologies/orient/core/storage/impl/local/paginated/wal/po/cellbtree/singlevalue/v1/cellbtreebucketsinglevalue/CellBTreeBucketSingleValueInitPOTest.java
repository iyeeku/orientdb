package com.orientechnologies.orient.core.storage.impl.local.paginated.wal.po.cellbtree.singlevalue.v1.cellbtreebucketsinglevalue;

import com.orientechnologies.common.directmemory.OByteBufferPool;
import com.orientechnologies.common.directmemory.OPointer;
import com.orientechnologies.orient.core.storage.cache.OCacheEntry;
import com.orientechnologies.orient.core.storage.cache.OCacheEntryImpl;
import com.orientechnologies.orient.core.storage.cache.OCachePointer;
import com.orientechnologies.orient.core.storage.impl.local.paginated.wal.po.PageOperationRecord;
import com.orientechnologies.orient.core.storage.index.sbtree.singlevalue.v1.OCellBTreeBucketSingleValue;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class CellBTreeBucketSingleValueInitPOTest {
  @Test
  public void testRedo() {
    final int pageSize = 256;
    final OByteBufferPool byteBufferPool = new OByteBufferPool(pageSize);
    try {
      final OPointer pointer = byteBufferPool.acquireDirect(false);
      final OCachePointer cachePointer = new OCachePointer(pointer, byteBufferPool, 0, 0);
      final OCacheEntry entry = new OCacheEntryImpl(0, 0, cachePointer);

      OCellBTreeBucketSingleValue bucket = new OCellBTreeBucketSingleValue(entry);
      bucket.init(true);

      final List<PageOperationRecord> operations = entry.getPageOperations();
      Assert.assertEquals(1, operations.size());

      Assert.assertTrue(operations.get(0) instanceof CellBTreeBucketSingleValueInitPO);

      final CellBTreeBucketSingleValueInitPO pageOperation = (CellBTreeBucketSingleValueInitPO) operations.get(0);

      final OPointer restoredPointer = byteBufferPool.acquireDirect(false);
      final OCachePointer restoredCachePointer = new OCachePointer(restoredPointer, byteBufferPool, 0, 0);
      final OCacheEntry restoredCacheEntry = new OCacheEntryImpl(0, 0, restoredCachePointer);

      pageOperation.redo(restoredCacheEntry);

      OCellBTreeBucketSingleValue restoredPage = new OCellBTreeBucketSingleValue(restoredCacheEntry);

      Assert.assertTrue(restoredPage.isLeaf());
      Assert.assertEquals(0, restoredPage.size());
      Assert.assertEquals(-1, restoredPage.getLeftSibling());
      Assert.assertEquals(-1, restoredPage.getRightSibling());

      byteBufferPool.release(pointer);
      byteBufferPool.release(restoredPointer);
    } finally {
      byteBufferPool.clear();
    }
  }
}
