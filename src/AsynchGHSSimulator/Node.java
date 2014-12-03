package AsynchGHSSimulator;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Created by priyanka on 11/25/14.
 */
public class Node implements Runnable {
  int UID;
  String name;
  int level;
  int core;
  String status = null;
  Map<Node, Edge> neighbors; // maps neighboring nodes to edges
  boolean terminated;
  BlockingQueue<Message> messageQueue = new ArrayBlockingQueue<Message>(1000, true);
  BlockingQueue<Message> deferredMsgs = new ArrayBlockingQueue<Message>(1000, true);
  List<Integer> receivedConnectFrom = new ArrayList<Integer>();
  List<Integer> sentConnectTo = new ArrayList<Integer>();


  Node(int UID) {
    this.UID = UID;
    System.out.print(this.UID);
    this.core = UID;
    this.name = "?";
    this.level = 0;
    neighbors = new HashMap<Node, Edge>();

//    addActionListener(new ActionListener() {
//      public void actionPerformed(ActionEvent ae) {
    //sendMessage(new Message(Message.WAKEUP,0,Node.this.UID));
//      }
//    });

    //(new Thread(this)).start();
  }

  Map getNeighbors() {
    return neighbors;
  }

  public void run() {
    try {
      receiveMessages();
    } catch (Exception e) {
      closeConnection(e);
    }
  }

  synchronized void sendMessage(Message m) {
    try {
      messageQueue.add(m);
      System.out.println("delivered " + m);
    } catch (Exception e) {
      closeConnection(e);
    }
  }

  void sendEdges() {
    final TreeMap<Integer, Integer> h = new TreeMap<Integer, Integer>();
    Iterator it = neighbors.keySet().iterator();
    while (it.hasNext()) {
      Node n = (Node) it.next();
      System.out.println(this.UID + " has a neighbour with uid " + n.UID + " and edge weight is " + neighbors.get(n).getCost());
      h.put(((Edge) neighbors.get(n)).getCost(), n.UID);

    }
    (new Thread() {
      public void run() {
        sendMessage(new Message(UID, h));
      }
    }).start();
  }

  Node findMinWeightNeighbour() {
    int minCost = 10000;
    Node minWeightNeighbour = null;
    for (Node n : neighbors.keySet()) {
      if (minCost > neighbors.get(n).getCost()) {
        minCost = neighbors.get(n).getCost();
        minWeightNeighbour = n;
      }
    }
    return minWeightNeighbour;
  }

  void receiveMessages() {
    try {
      while (true) {
        Message m = (Message) messageQueue.poll();
        switch (m.messageType) {
          case Message.REGISTRATION:
            name = (String) m.data;
            break;
          case Message.WAKEUP:
            Node minWeightNeighbour = findMinWeightNeighbour();
            if (!sentConnectTo.contains(minWeightNeighbour.UID)) {
              neighbors.get(minWeightNeighbour).forwardMessage(this, minWeightNeighbour, new Message(Message.CONNECT, UID, minWeightNeighbour.UID, core, level));
              this.sentConnectTo.add(minWeightNeighbour.UID);
            }
            break;
          //this.closeConnection("node sent Wakeup message");
          //break;
          case Message.INITIATE:
          case Message.CONNECT:
            if (m.sender != UID && m.destination == UID) {
              connect(m.sender, m.destination, m.core, m.level);
            } else {
              forwardMessage(m);
            }
            break;
          case Message.INFORM:
            level = m.level;
            core = m.core;
            // don't break
          default:
            forwardMessage(m);
        }
      }
    } catch (Exception e) {
      closeConnection(e);
    }
  }

  void connect(int src, int dest, int core, int level) {
    receivedConnectFrom.add(dest);
    if (sentConnectTo.contains(src) && receivedConnectFrom.contains(dest)) {
      this.core = Math.max(src, dest);
      this.level++;
      //if (this.core == UID) // WE'RE THE NEW ROOT, SO START THE MWOE SEARCH
      //send Initiate(this.core,this.level) to self // start broadcast
    }
  }

  void forwardMessage(Message m) {
    if (m.destination == UID)
      sendMessage(m);
    else {
      Node n = (Node) MSTviewer.nodes.get(m.destination);
      Edge e = (Edge) neighbors.get(n);
      e.forwardMessage(this, n, m);
    }
  }

  void closeConnection(Object reason) {
    if (!terminated) { // print only if not terminated yet
      System.out.println("Closing socket to " + name + " due to: " + reason);
      if (reason instanceof Exception)
        ((Exception) reason).printStackTrace();
    }
    terminated = true;
  }
}
