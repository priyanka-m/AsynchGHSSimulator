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
  Node parent = null;
  Node minChild = null;
  Edge mwoe = null;
  Map<Node, Edge> neighbors; // maps neighboring nodes to edges
  boolean terminated;
  BlockingQueue<Message> messageQueue = new ArrayBlockingQueue<Message>(1000, true);
  BlockingQueue<Message> deferredMsgs = new ArrayBlockingQueue<Message>(1000, true);
  List<Integer> receivedConnectFrom = new ArrayList<Integer>();
  List<Integer> sentConnectTo = new ArrayList<Integer>();
  ArrayList<Edge> branchEdges = new ArrayList<Edge>();
  ArrayList<Edge> basicEdges = new ArrayList<Edge>();
  ArrayList<Edge> rejectedEdges = new ArrayList<Edge>();


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
            Initiate(m.sender, m.core, m.level);
          case Message.CONNECT:
            if (m.sender != UID && m.destination == UID) {
              receivedConnectFrom.add(m.sender);
              if (sentConnectTo.contains(m.sender) && receivedConnectFrom.contains(m.destination)) {
                connect(m.sender, m.destination, m.core, m.level);
              } else {
                deferredMsgs.add(m);
              }
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

  Node findNodeIncidentOnEdge(Edge incidentEdge) {
    for (Node n : neighbors.keySet()) {
      if (neighbors.get(n).equals(incidentEdge))
        return n;
    }
    return null;
  }

  Edge findLowestCostBasicEdge() {
    int minCost = 100000;
    Edge minCostEdge = null;
    for (Edge e : basicEdges) {
      if (e.cost < minCost)
        minCost = e.cost;
      minCostEdge = e;
    }
    return minCostEdge;
  }

  void Initiate(int sender, int core, int level) {
    status = "SEARCHING";
    this.core = core;
    this.level = level;
    processDeferredTests();
    this.parent = (Node) MSTviewer.nodes.get(sender);
    this.minChild = null;

    ArrayList<Edge> waitingForReport = branchEdges;
    //let waitingForReport contain all branch edges (besides E, if sender != self)
    for (Edge e : branchEdges) {
      Node neighbour = findNodeIncidentOnEdge(e);
      if (neighbour != null)
        e.forwardMessage(this, neighbour, new Message(Message.INITIATE, this.UID, findNodeIncidentOnEdge(e).UID, this.core, this.level));
    }
    //send "Initiate(core,level)" over all branch edges (besides E, sender != self)
    mwoe.cost = 100000;
    while (!basicEdges.isEmpty() && (mwoe == null || mwoe.cost > findLowestCostBasicEdge().cost)) {
      Node neighbour = findNodeIncidentOnEdge(findLowestCostBasicEdge());
      findLowestCostBasicEdge().forwardMessage(this, neighbour, new Message(Message.TEST, this.UID, neighbour.UID, this.core, this.level));
      //wait for a response for the test message.
      try {
        Thread.sleep(1000);
      } catch (Exception e) {
        System.out.println(e);
      }
    }

    if (waitingForReport.isEmpty()) {
      if (parent != this) {
        status = 'FOUND';
        send "Report(mwoe)" to parent  // this will be the minimum in subtree
      } else if (mwoe.cost != 100000)
        send "ChangeRoot" to self
      else
      send "AllDone" on all branchEdges
    }
  }

  void connect(int src, int dest, int core, int level) {
    //if (this.level > level) {// *** ABSORB THE OTHER FRAGMENT ***
    //if (status == FOUND)  // MWOE can't be in the absorbed fragment
    //send Inform(core,level) over E
    //if (status == SEARCHING) {// MWOE might be in the absorbed fragment
    //add E to waitingForReport
    //send Initiate(core,level) over E
    //}
    //} else {// levels are the same, so *** MERGE WITH THE OTHER FRAGMENT ***
    // may be SLEEPING or may have sent Connect on E
    this.core = Math.max(src, dest);
    this.level++;
    processDeferredTests();
    if (this.core == this.UID) {// WE'RE THE NEW ROOT, SO START THE MWOE SEARCH
      sendMessage(new Message(Message.INITIATE, this.UID, this.UID, this.core, this.level));// start broadcast
    }
  }

  void processDeferredTests() {
//    for each message M in deferredTests
//    if (this.core == M.core)
//      send "Reject" to M's sender
//    remove M from deferredTests
//    else if (this.level >= level)
//      send "Accept" to M's sender
//    remove M from deferredTests
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
