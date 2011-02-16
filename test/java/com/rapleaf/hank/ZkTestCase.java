package com.rapleaf.hank;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.BindException;
import java.net.Socket;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.server.NIOServerCnxn;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.apache.zookeeper.server.NIOServerCnxn.Factory;

import com.rapleaf.hank.util.ZooKeeperUtils;

public class ZkTestCase extends BaseTestCase {
  private static final Logger LOG = Logger.getLogger(BaseTestCase.class);

  private static final int TICK_TIME = 2000;
  private static final int CONNECTION_TIMEOUT = 30000;

  private final String zkRoot;
  private ZooKeeper zk;

  private final String zkDir = System.getProperty("zk_dir", "/tmp/zk_in_tests");
  private Factory standaloneServerFactory;

  private int zkClientPort;

  private boolean startedZk = false;

  public ZkTestCase() throws Exception {
    super();
    zkRoot = "/" + getClass().getSimpleName();

  }

  private int setupZkServer() throws Exception {
    File zkDirFile = new File(zkDir);
    FileUtils.deleteDirectory(zkDirFile);
    zkDirFile.mkdirs();

    ZooKeeperServer server = new ZooKeeperServer(zkDirFile, zkDirFile, TICK_TIME);

    int clientPort = 2000;
    while (true) {
      try {
        standaloneServerFactory =
          new NIOServerCnxn.Factory(clientPort);
      } catch (BindException e) {
        LOG.info("Failed binding ZK Server to client port: " + clientPort);
        //this port is already in use. try to use another
        clientPort++;
        continue;
      }
      break;
    }
    standaloneServerFactory.startup(server);

    if (!waitForServerUp(clientPort, CONNECTION_TIMEOUT)) {
      throw new IOException("Waiting for startup of standalone server");
    }
    startedZk = true;
    return clientPort;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    zkClientPort = setupZkServer();

    zk = new ZooKeeper("127.0.0.1:" + zkClientPort, 1000000, null);

    ZooKeeperUtils.deleteNodeRecursively(zk, zkRoot);
    ZooKeeperUtils.createNodeRecursively(zk, zkRoot);
  }

  private static boolean waitForServerUp(int port, long timeout) {
    long start = System.currentTimeMillis();
    while (true) {
      try {
        Socket sock = new Socket("localhost", port);
        BufferedReader reader = null;
        try {
          OutputStream outstream = sock.getOutputStream();
          outstream.write("stat".getBytes());
          outstream.flush();

          Reader isr = new InputStreamReader(sock.getInputStream());
          reader = new BufferedReader(isr);
          String line = reader.readLine();
          if (line != null && line.startsWith("Zookeeper version:")) {
            return true;
          }
        } finally {
          sock.close();
          if (reader != null) {
            reader.close();
          }
        }
      } catch (IOException e) {
        // ignore as this is expected
        LOG.info("server localhost:" + port + " not up " + e);
      }

      if (System.currentTimeMillis() > start + timeout) {
        break;
      }
      try {
        Thread.sleep(250);
      } catch (InterruptedException e) {
        // ignore
      }
    }
    return false;
  }

  public String getRoot() {
    return zkRoot;
  }

  public ZooKeeper getZk() {
    return zk;
  }

  protected void create(String path) throws Exception {
    create(path, (byte[]) null);
  }

  protected void create(String path, String data) throws Exception {
    create(path, data.getBytes());
  }

  protected void create(String path, byte[] data) throws Exception {
    getZk().create(path, data, Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    shutdownZk();
  }

  public void shutdownZk() throws Exception {
    if (!startedZk) {
      return;
    }

    zk.close();
    zk = null;
    
    standaloneServerFactory.shutdown();
    if (!waitForServerDown(zkClientPort, CONNECTION_TIMEOUT)) {
      throw new IOException("Waiting for shutdown of standalone server");
    }

    startedZk = false;
  }

  // XXX: From o.a.zk.t.ClientBase
  private boolean waitForServerDown(int port, long timeout) {
    long start = System.currentTimeMillis();
    while (true) {
      try {
        Socket sock = new Socket("localhost", port);
        try {
          OutputStream outstream = sock.getOutputStream();
          outstream.write("stat".getBytes());
          outstream.flush();
        } finally {
          sock.close();
        }
      } catch (IOException e) {
        return true;
      }

      if (System.currentTimeMillis() > start + timeout) {
        break;
      }
      try {
        Thread.sleep(250);
      } catch (InterruptedException e) {
        // ignore
      }
    }
    return false;
  }

  public int getZkClientPort() {
    return zkClientPort;
  }
}
