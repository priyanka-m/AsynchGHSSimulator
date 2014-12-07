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
  int mwoe;
  String status = null;
  Node parent = null;
  Node minChild = null;
  //Edge MWOE = new Edge();
  Map<Node, Edge> neighbors; // maps neighboring nodes to edges
  boolean terminated;
  BlockingQueue<Message> messageQueue = new ArrayBlockingQueue<Message>(1000, true);
  BlockingQueue<Message> deferredMsgs = new ArrayBlockingQueue<Message>(1000, true);
  List<Integer> receivedConnectFrom = new ArrayList<Integer>();
  List<Integer> sentConnectTo = new ArrayList<Integer>();
  List<Integer> receivedAcceptFrom = new ArrayList<Integer>();
  List<Integer> receivedRejectFrom = new ArrayList<Integer>();
  ArrayList<Edge> waitingForReport = new ArrayList<Edge>();
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
        if (m != null) {
          switch (m.messageType) {
            case Message.REGISTRATION:
              name = (String) m.data;
              break;
            case Message.WAKEUP:
              Node minWeightNeighbour = findMinWeightNeighbour();
              if (!sentConnectTo.contains(minWeightNeighbour.UID)) {
                neighbors.get(minWeightNeighbour).forwardMessage(this, minWeightNeighbour, new Message(Message.CONNECT, UID, minWeightNeighbour.UID, core, level));
                this.sentConnectTo.add(minWeightNeighbour.UID);
                System.out.println("sent connect to of " + UID + " contains " + minWeightNeighbour.UID);
              }
              break;
            case Message.INITIATE:
              Initiate(m.sender, m.core, m.level);
              break;
            case Message.TEST:
              Test(m);
            case Message.ACCEPT:
              Accept(m.sender);
              break;
            case Message.REJECT:
              Reject(m.sender);
              break;
            case Message.REPORT:
              Report(m.sender, m.cost);
              break;
            case Message.CONNECT:
              if (m.sender != UID && m.destination == UID) {
                receivedConnectFrom.add(m.sender);
                if (sentConnectTo.contains(m.sender) && receivedConnectFrom.contains(m.sender)) {
                  System.out.println("connect received " + m.sender + m.destination);
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
      }
    } catch (Exception e) {
      closeConnection(e);
    }
  }

  Node findNodeIncidentOnEdge(Edge incidentEdge) {
    for (Node n : neighbors.keySet()) {
      if (neighbors.get(n).equals(incidentEdge)) {
        System.out.println("node found with id " + n.UID);
        return n;
      }
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
    //processDeferredTests();
    this.parent = (Node) MSTviewer.nodes.get(sender);
    this.minChild = null;

    waitingForReport = branchEdges;
    //let waitingForReport contain all branch edges (besides E, if sender != self)
    for (Edge e : branchEdges) {
      Node neighbour = findNodeIncidentOnEdge(e);
      System.out.println(UID + " sending initiate to " + neighbour.UID);
      System.out.println(UID + " sending initiate to " + neighbour.UID);
      if (neighbour != null)
        e.forwardMessage(this, neighbour, new Message(Message.INITIATE, this.UID, findNodeIncidentOnEdge(e).UID, this.core, this.level));
    }
    //send "Initiate(core,level)" over all branch edges (besides E, sender != self)
    //MWOE.cost =
    mwoe = 100000;
    Edge lowestCostBasicEdge = findLowestCostBasicEdge();
    while (!basicEdges.isEmpty() && (mwoe == 100000  || mwoe > lowestCostBasicEdge.cost)) {
      Node neighbour = findNodeIncidentOnEdge(lowestCostBasicEdge);
      lowestCostBasicEdge.forwardMessage(this, neighbour, new Message(Message.TEST, this.UID, neighbour.UID, this.core, this.level));
      //wait for a response for the test message.
      while (!receivedAcceptFrom.contains(neighbour.UID) || !receivedRejectFrom.contains(neighbour.UID)) {}
    }
    if (waitingForReport.isEmpty()) {
      if (parent != this) {
        status = "FOUND";
        parent.sendMessage(new Message(Message.REPORT, UID, parent.UID, mwoe));// this will be the minimum in subtree
      } else if (mwoe != 100000) {
        sendMessage(new Message(Message.CHANGE_ROOT, UID, UID));
      } else
        for (Edge branchEdge : branchEdges) {
          Node n = findNodeIncidentOnEdge(branchEdge);
          branchEdge.forwardMessage(this, n, new Message(Message.ALL_DONE, UID, n.UID));
        }
    }
  }

  void Test(Message m) {
    if (this.core == m.core) {// in the same fragment
      neighbors.get(MSTviewer.nodes.get(m.sender)).forwardMessage(this, MSTviewer.nodes.get(m.sender), new Message(Message.REJECT, UID, m.sender));
      rejectedEdges.add(neighbors.get(MSTviewer.nodes.get(m.sender)));
    } else if (this.level >= m.level) {// can't be in the same fragment
      neighbors.get(MSTviewer.nodes.get(m.sender)).forwardMessage(this, MSTviewer.nodes.get(m.sender), new Message(Message.ACCEPT, UID, m.sender));
    } else // don't know yet because we haven't reached that level
      deferredMsgs.add(m);
  }

  void Accept(int sender) {
    if (neighbors.get(MSTviewer.nodes.get(sender)).getCost() < mwoe) {
      minChild = null;
      mwoe = neighbors.get(MSTviewer.nodes.get(sender)).getCost();
    }
  }

  void Reject(int sender) {
    receivedRejectFrom.add(sender);
    rejectedEdges.add(neighbors.get(MSTviewer.nodes.get(sender)));
    basicEdges.remove(neighbors.get(MSTviewer.nodes.get(sender)));
  }

  void Report(int sender, int cost) {
    waitingForReport.remove(neighbors.get(MSTviewer.nodes.get(sender)));
    if (cost < mwoe)
      minChild = MSTviewer.nodes.get(sender);
      mwoe = cost;
  }

  synchronized void connect(int src, int dest, int core, int level) {
    System.out.println("connect called");
    branchEdges.add(neighbors.get(dest));
    basicEdges.remove(neighbors.get(dest));
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
    System.out.println("core is " + this.core);
    this.level++;
    //processDeferredTests();
    if (this.core == this.UID) {// WE'RE THE NEW ROOT, SO START THE MWOE SEARCH
      System.out.println("new leader is " + this.core);
      sendMessage(new Message(Message.INITIATE, this.UID, this.UID, this.core, this.level));// start broadcast
    }
  }

  void processDeferredTests() {

      Message m;
      for (int i=0;i<deferredMsgs.size();i++){
        m = (Message) deferredMsgs.poll();
        if (this.core == m.core){
            if(receivedConnectFrom.contains(m.sender)){  
                neighbors.get(MSTviewer.nodes.get(m.sender)).forwardMessage(this, MSTviewer.nodes.get(m.sender), new Message(Message.REJECT,this.UID,m.sender));
                deferredMsgs.remove(m);
            }
        
     //       rejectMessage(this.UID,m.sender,new Message(Message.REJECT,this.UID,m.sender));
        }
        else if (this.level >= level){
            if (neighbors.get(this).getCost() < mwoe){
        //      minChild = null;
                mwoe = neighbors.get(this).getCost();
                neighbors.get(MSTviewer.nodes.get(m.sender)).forwardMessage(this, MSTviewer.nodes.get(m.sender), new Message(Message.ACCEPT,this.UID,m.sender));
                deferredMsgs.remove(m);
            }
     
      //      acceptMessage(this,m.sender,new Message(Message.ACCEPT,this.UID,m.sender));
        }
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

