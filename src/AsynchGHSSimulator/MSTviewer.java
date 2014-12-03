package AsynchGHSSimulator;

import java.util.ArrayList;
import java.util.Iterator;

public class MSTviewer implements Runnable {
  //data:
  static final int MAX_NODES = 50; // limit on the number of nodes in graph
  boolean started; // true when the algorithm has been started up by user
  public static ArrayList<Node> nodes = new ArrayList<Node>();   // Node objects indexed by unique IDs
  static int edgeCount = 0, nodeCount = 5;
  static int[][] connections = {{0, 1, 1, 0, 0}, {1, 0, 1, 1, 0}, {1, 1, 0, 1, 1}, {0, 1, 1, 0, 1}, {0, 0, 1, 1, 0}};
  ;

  synchronized void setStarted(boolean b) {
    started = b;
  }

  synchronized boolean isStarted() {
    return started;
  }

  public MSTviewer() {
    //setStarted(true);
  }

  public void run() {
    //while (true) {
    try {
      acceptRegistration();
      createEdges();
      startAlgorithm();
      //sendEdges();
      //waitForTermination();
    } catch (Exception e) {
      System.out.println(" stopped ");
      e.printStackTrace();
    }
    //}
  }

  void startAlgorithm() {
    Iterator it = nodes.iterator();
    while (it.hasNext()) {
      Node node = (Node) it.next();
      if (node != null) // skip dummy entry 0
        node.sendMessage(new Message(Message.WAKEUP, 100, node.UID));
      (new Thread(node)).start();
    }
  }

  void closeConnections() {
    Iterator it = nodes.iterator();
    while (it.hasNext()) {
      Node n = (Node) it.next();
      if (n != null) {
        n.closeConnection("normal termination");
      }
    }
  }

  void acceptRegistration() {
    //System.out.print(nodeCount);
    for (int i = 0; i < nodeCount; i++) {
      if ((nodes.size() < MAX_NODES + 1 || nodes.size() <= 3) && !isStarted()) {
        try {
          nodes.add(new Node(nodes.size()));
          //System.out.print(nodes.size());
        } catch (Exception e) {
          System.out.println(e);
        }
      }
    }
    //System.out.print(nodes.size());
  }

  void createEdges() {
    // first, create a cycle through the graph to ensure connectedness
    for (int i = 0; i < nodeCount; i++) {
      for (int j = 0; j < nodeCount; j++) {
        if (connections[i][j] == 1) {
          new Edge(nodes.get(i), nodes.get(j));
          // To avoid creation of another edge from j to i. We can alter the array connections because
          // we don't need it anymore.
          connections[i][j] = 0;
          connections[j][i] = 0;
        }
      }

    }
  }

  // Sends a copy of all edges to all nodes.
  void sendEdges() {
    Iterator it = nodes.iterator();
    while (it.hasNext()) {
      Node node = (Node) it.next();
      if (node != null) // skip dummy entry 0
        System.out.println(" sending data to neighbours of node " + node.UID);
      node.sendEdges();
    }
  }

  /*void waitForTermination() {
    while (isStarted())
      synchronized(endButton) {
        try {endButton.wait();} catch (InterruptedException ie) {}
      }
    System.out.println("\n SESSION TERMINATED BY USER \n");
  }*/

  public static void main(String[] args) {
    int[] arrayIds = null;
    // TODO: fix input file reading
    //StreamTokenizer tokenizer = null;

    //try{
//      tokenizer = new StreamTokenizer(new FileReader("../data/input.txt"));
//      tokenizer.slashSlashComments(true);
//      tokenizer.eolIsSignificant(false);
//      tokenizer.nextToken();
//      nodeCount = (int)tokenizer.nval;
//      arrayIds = new int[nodeCount];
//      connections = new int[nodeCount][nodeCount];
//
//      for(int i=0;i<nodeCount;i++){
//        tokenizer.nextToken();
//        if (tokenizer.ttype == StreamTokenizer.TT_NUMBER){
//          arrayIds[i] = (int)tokenizer.nval;
//        }
//      }
//      for(int i=0;i<nodeCount;i++){
//        for(int j=0;j<nodeCount;j++){
//          tokenizer.nextToken();
//          if (tokenizer.ttype == StreamTokenizer.TT_NUMBER){
//            connections[i][j] = (int)tokenizer.nval;
//          }
//        }
//      }
//    }catch(FileNotFoundException e){
//      System.out.println("Exception::" +e);
//    } catch (IOException e2) {
//      System.out.println("Exception::" +e2);
//    }
    MSTviewer viewer = new MSTviewer();
    (new Thread(viewer)).start();
  }
}
