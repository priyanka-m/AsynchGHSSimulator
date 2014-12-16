package AsynchGHSSimulator;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.StreamTokenizer;
import java.util.ArrayList;
import java.util.Iterator;
/**
* @author Shraddha Patel, Priyanka Menghani
*/
public class MSTviewer implements Runnable {
  static final int MAX_NODES = 50; // limit on the number of nodes in graph
  boolean started; // true when the algorithm has been started up by user
  public static ArrayList<Node> nodes = new ArrayList<Node>();   // Node objects indexed by unique IDs
  static int edgeCount = 0; //number of edges
  static int  nodeCount ;   //number of nodes
  static int[][] connections ; //keeps track of edges between two nodes

  synchronized void setStarted(boolean b) {
    started = b;
  }

  synchronized boolean isStarted() {
    return started;
  }

  public MSTviewer() {
  }

  @Override
  /**
   * Method to start the algorithm
   */
  public void run() {
    try {
      acceptRegistration();
      createEdges();
      startAlgorithm();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  /**
  * Method to initialize nodes with their IDs
  */
  void acceptRegistration() {
    for (int i = 0; i < nodeCount; i++) {
      if ((nodes.size() < MAX_NODES + 1 || nodes.size() <= 3) && !isStarted()) {
        try {
          nodes.add(new Node(nodes.size()));
        } catch (Exception e) {
          System.out.println(e);
        }
      }
    }
  }
  /**
  * Method to create link between two nodes
  */
  void createEdges() {
    // first, create a cycle through the graph to ensure it is connected
    for (int i = 0; i < nodeCount; i++) {
      for (int j = 0; j < nodeCount; j++) {
        if (connections[i][j] > 0) {
          new Edge(nodes.get(i), nodes.get(j), connections[i][j]);
          // To avoid creation of another edge from j to i. We can alter the array connections because
          // we don't need it anymore.
          connections[i][j] = 0;
          connections[j][i] = 0;
        }
      }

    }
  }
  /**
  * Method to start the actual implementation of the algorithm
  */
  void startAlgorithm() {
    System.out.println("Starting the algorithm, 100 represents the main thread");
    Iterator it = nodes.iterator();
    while (it.hasNext()) {
      Node node = (Node) it.next();
      if (node != null) // skip dummy entry 0
        node.sendMessage(new Message(Message.WAKEUP, 100, node.UID));
      (new Thread(node)).start();
    }
  }
  /**
  * Method to terminate the algorithm
  */
  void closeConnections() {
    Iterator it = nodes.iterator();
    while (it.hasNext()) {
      Node n = (Node) it.next();
      if (n != null) {
        n.closeConnection("normal termination");
      }
    }
  }
 /**
  * Main method to read node count and connectivity matrix
  * @param args
  */
  public static void main(String[] args) {
    int[] arrayIds = null;
    StreamTokenizer tokenizer = null;

    try{
      tokenizer = new StreamTokenizer(new FileReader("input.txt"));
      tokenizer.slashSlashComments(true);
      tokenizer.eolIsSignificant(false);
      tokenizer.nextToken();
      nodeCount = (int)tokenizer.nval;
      arrayIds = new int[nodeCount];
      connections = new int[nodeCount][nodeCount];

      for(int i=0;i<nodeCount;i++){
        tokenizer.nextToken();
        if (tokenizer.ttype == StreamTokenizer.TT_NUMBER){
          arrayIds[i] = (int)tokenizer.nval;
        }
      }
      for(int i=0;i<nodeCount;i++){
        for(int j=0;j<nodeCount;j++){
          tokenizer.nextToken();
          if (tokenizer.ttype == StreamTokenizer.TT_NUMBER){
            connections[i][j] = (int)tokenizer.nval;
          }
        }
      }
    }catch(FileNotFoundException e){
      System.out.println("Exception::" +e);
    } catch (IOException e) {
      System.out.println("Exception::" +e);
    }
    MSTviewer viewer = new MSTviewer();
    (new Thread(viewer)).start();
  }
}
