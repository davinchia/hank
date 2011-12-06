/**
 *  Copyright 2011 Rapleaf
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.rapleaf.hank.storage.curly;

import com.rapleaf.hank.coordinator.Domain;
import com.rapleaf.hank.coordinator.DomainVersion;
import com.rapleaf.hank.coordinator.mock.MockDomain;
import com.rapleaf.hank.coordinator.mock.MockDomainVersion;
import com.rapleaf.hank.storage.IncrementalPartitionUpdaterTestCase;
import com.rapleaf.hank.storage.IncrementalUpdatePlan;
import com.rapleaf.hank.storage.LocalPartitionRemoteFileOps;
import org.apache.commons.lang.NotImplementedException;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class TestAbstractCurlyPartitionUpdater extends IncrementalPartitionUpdaterTestCase {

  private final DomainVersion v0 = new MockDomainVersion(0, 0l);
  private final DomainVersion v1 = new MockDomainVersion(1, 0l);
  private final DomainVersion v2 = new MockDomainVersion(2, 0l);
  private final Domain domain = new MockDomain("domain") {
    @Override
    public DomainVersion getVersionByNumber(int versionNumber) {
      switch (versionNumber) {
        case 0:
          return v0;
        case 1:
          return v1;
        case 2:
          return v2;
        default:
          throw new RuntimeException("Unknown version: " + versionNumber);
      }
    }
  };
  private AbstractCurlyPartitionUpdater updater;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    this.updater = new AbstractCurlyPartitionUpdater(domain,
        new LocalPartitionRemoteFileOps(remotePartitionRoot, 0),
        localPartitionRoot) {
      @Override
      protected void runUpdateCore(DomainVersion currentVersion,
                                   DomainVersion updatingToVersion,
                                   IncrementalUpdatePlan updatePlan,
                                   String updateWorkRoot) throws IOException {
        throw new NotImplementedException();
      }
    };

    if (!new File(updateWorkRoot).mkdir()) {
      throw new IOException("Failed to create update work root");
    }
  }

  public void testGetDomainVersionParent() throws IOException {
    // Parent is null when base found
    makeRemoteFile("0/00001.base.cueball");
    makeRemoteFile("0/00001.base.curly");
    assertNull(updater.getParentDomainVersion(v1));
    deleteRemoteFile("0/00001.base.cueball");
    deleteRemoteFile("0/00001.base.curly");

    // Parent is previous version number when delta found
    makeRemoteFile("0/00001.delta.cueball");
    makeRemoteFile("0/00001.delta.curly");
    assertEquals(v0, updater.getParentDomainVersion(v1));
    deleteRemoteFile("0/00001.delta.cueball");
    deleteRemoteFile("0/00001.delta.curly");
  }

  public void testDetectCurrentVersionNumber() throws IOException {
    // Null when there is no version
    assertEquals(null, updater.detectCurrentVersionNumber());

    // Nothing when there is only cueball files
    makeLocalFile("00001.base.cueball");
    assertEquals(null, updater.detectCurrentVersionNumber());
    deleteLocalFile("00001.base.cueball");

    // Nothing when there is only a delta
    makeLocalFile("00001.delta.cueball");
    makeLocalFile("00001.delta.curly");
    assertEquals(null, updater.detectCurrentVersionNumber());
    deleteLocalFile("00001.delta.cueball");
    deleteLocalFile("00001.delta.curly");

    // Correct number when there is a base
    makeLocalFile("00001.base.cueball");
    makeLocalFile("00001.base.curly");
    assertEquals(Integer.valueOf(1), updater.detectCurrentVersionNumber());
    deleteLocalFile("00001.base.cueball");
    deleteLocalFile("00001.base.curly");

    // Most recent base
    makeLocalFile("00001.base.cueball");
    makeLocalFile("00001.base.curly");
    makeLocalFile("00002.base.cueball");
    makeLocalFile("00002.base.curly");
    assertEquals(Integer.valueOf(2), updater.detectCurrentVersionNumber());
    deleteLocalFile("00001.base.cueball");
    deleteLocalFile("00001.base.curly");
    deleteLocalFile("00002.base.cueball");
    deleteLocalFile("00002.base.curly");
  }

  public void testGetCachedVersions() throws IOException {
    Set<DomainVersion> versions = new HashSet<DomainVersion>();

    updater.ensureCacheExists();

    // Empty cache
    assertEquals(versions, updater.detectCachedBases());
    assertEquals(versions, updater.detectCachedDeltas());

    // Do not consider cueball files only
    makeLocalCacheFile("00001.base.cueball");
    assertEquals(Collections.<DomainVersion>emptySet(), updater.detectCachedBases());
    assertEquals(Collections.<DomainVersion>emptySet(), updater.detectCachedDeltas());
    deleteLocalCacheFile("00001.base.cueball");

    // Delta only
    makeLocalCacheFile("00001.delta.cueball");
    makeLocalCacheFile("00001.delta.curly");
    assertEquals(Collections.<DomainVersion>emptySet(), updater.detectCachedBases());
    assertEquals(Collections.<DomainVersion>singleton(v1), updater.detectCachedDeltas());
    deleteLocalCacheFile("00001.delta.cueball");
    deleteLocalCacheFile("00001.delta.curly");

    // Use bases
    makeLocalCacheFile("00000.base.cueball");
    makeLocalCacheFile("00000.base.curly");
    assertEquals(Collections.<DomainVersion>singleton(v0), updater.detectCachedBases());
    assertEquals(Collections.<DomainVersion>emptySet(), updater.detectCachedDeltas());
    deleteLocalCacheFile("00000.base.cueball");
    deleteLocalCacheFile("00000.base.curly");

    // Use multiple bases
    makeLocalCacheFile("00000.base.cueball");
    makeLocalCacheFile("00000.base.curly");
    makeLocalCacheFile("00001.base.cueball");
    makeLocalCacheFile("00001.base.curly");
    versions.add(v0);
    versions.add(v1);
    assertEquals(versions, updater.detectCachedBases());
    assertEquals(Collections.<DomainVersion>emptySet(), updater.detectCachedDeltas());
    versions.clear();
    deleteLocalCacheFile("00000.base.cueball");
    deleteLocalCacheFile("00000.base.curly");
    deleteLocalCacheFile("00001.base.cueball");
    deleteLocalCacheFile("00001.base.curly");
  }

  public void testFetchVersion() throws IOException {
    String fetchRootName = "_fetch";
    String fetchRoot = localPartitionRoot + "/" + fetchRootName;
    new File(fetchRoot).mkdir();

    // Fetch delta
    makeRemoteFile("0/00000.delta.cueball");
    makeRemoteFile("0/00000.delta.curly");
    updater.fetchVersion(v0, fetchRoot);
    deleteRemoteFile("0/00000.delta.cueball");
    deleteRemoteFile("0/00000.delta.curly");
    assertTrue(existsLocalFile(fetchRootName + "/00000.delta.cueball"));
    assertTrue(existsLocalFile(fetchRootName + "/00000.delta.curly"));

    // Fetch base
    makeRemoteFile("0/00000.base.cueball");
    makeRemoteFile("0/00000.base.curly");
    updater.fetchVersion(v0, fetchRoot);
    deleteRemoteFile("0/00000.base.cueball");
    deleteRemoteFile("0/00000.base.curly");
    assertTrue(existsLocalFile(fetchRootName + "/00000.base.cueball"));
    assertTrue(existsLocalFile(fetchRootName + "/00000.base.curly"));
  }

}