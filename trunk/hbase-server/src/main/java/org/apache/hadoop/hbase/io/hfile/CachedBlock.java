/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hbase.io.hfile;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.hbase.io.HeapSize;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.ClassSize;

/**
 * Represents an entry in the {@link LruBlockCache}.
 *
 * <p>Makes the block memory-aware with {@link HeapSize} and Comparable
 * to sort by access time for the LRU.  It also takes care of priority by
 * either instantiating as in-memory or handling the transition from single
 * to multiple access.
 */
@InterfaceAudience.Private
public class CachedBlock implements HeapSize, Comparable<CachedBlock> {

  public final static long PER_BLOCK_OVERHEAD = ClassSize.align(
    ClassSize.OBJECT + (3 * ClassSize.REFERENCE) + (2 * Bytes.SIZEOF_LONG) +
    ClassSize.STRING + ClassSize.BYTE_BUFFER);

  static enum BlockPriority {
    /**
     * Accessed a single time (used for scan-resistance)
     */
    SINGLE,
    /**
     * Accessed multiple times
     */
    MULTI,
    /**
     * Block from in-memory store
     */
    MEMORY
  };

  private final BlockCacheKey cacheKey;
  private final Cacheable buf;
  private volatile long accessTime;
  private long size;
  private BlockPriority priority;
  private volatile long numAccesses = 0;

  public int getCustomId() {
    return customId;
  }

  public void setCustomId(int customId) {
    this.customId = customId;
  }

  // ID of the workload that placed this block in cache.
  private int customId;

  public CachedBlock(BlockCacheKey cacheKey, Cacheable buf, long accessTime) {
    this(cacheKey, buf, accessTime, false);
  }

  public CachedBlock(BlockCacheKey cacheKey, Cacheable buf, long accessTime,
      boolean inMemory) {
    this.cacheKey = cacheKey;
    this.buf = buf;
    this.accessTime = accessTime;
    this.numAccesses = 0;
    // We approximate the size of this class by the size of its name string
    // plus the size of its byte buffer plus the overhead associated with all
    // the base classes. We also include the base class
    // sizes in the PER_BLOCK_OVERHEAD variable rather than align()ing them with
    // their buffer lengths. This variable is used elsewhere in unit tests.
    this.size = ClassSize.align(cacheKey.heapSize())
        + ClassSize.align(buf.heapSize()) + PER_BLOCK_OVERHEAD;
    if(inMemory) {
      this.priority = BlockPriority.MEMORY;
    } else {
      this.priority = BlockPriority.SINGLE;
    }
    customId = 0;
  }

  /**
   * Block has been accessed.  Update its local access time.
   */
  static boolean btlogged = false;
  private static final Log LOG = LogFactory.getLog(CachedBlock.class);
  public void access(long accessTime, long threshold, int customid) {
    this.accessTime = accessTime;

    if (this.priority == BlockPriority.SINGLE) { // && numAccesses > threshold) { // TODO(CACHECHANGE): BUCKETTHROTTLING ENABLED
      this.priority = BlockPriority.MULTI;
    }
  }

  public void incrementNumAccesses() {
    this.numAccesses++;
  }

  public long getNumAccesses() {
    return this.numAccesses;
  }

  public long heapSize() {
    return size;
  }

  @Override
  public int compareTo(CachedBlock that) {
    if(this.accessTime == that.accessTime) return 0;
    return this.accessTime < that.accessTime ? 1 : -1;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    CachedBlock other = (CachedBlock) obj;
    return compareTo(other) == 0;
  }

  public Cacheable getBuffer() {
    return this.buf;
  }

  public BlockCacheKey getCacheKey() {
    return this.cacheKey;
  }

  public BlockPriority getPriority() {
    return this.priority;
  }
}
