package AynchGHSSimulator;

import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeMap;

/**
 * Created by priyanka on 11/25/14.
 */
public class Node implements Runnable {
  int UID;
  String name;
  int level;
  int core;
  HashMap neighbors; // maps neighboring nodes to edges
  boolean terminated;

  Node(int UID) {
    this.UID = UID;
    this.core = UID;
    this.name = "?";
    neighbors = new HashMap();

//    addActionListener(new ActionListener() {
//      public void actionPerformed(ActionEvent ae) {
//        sendMessage(new Message(Message.WAKEUP,0,Node.this.UID));
//      }
//    });

    //(new Thread(this)).start();
  }

  HashMap getNeighbors() {
    return neighbors;
  }

  public void run() {
    try {
//      receiveMessages();
    } catch (Exception e) {
      closeConnection(e);
    }
  }

  synchronized void sendMessage(Message m) {
    try {
 //     oos.writeObject(m);
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
      h.put(((Edge) neighbors.get(n)).getCost(), n.UID);

    }
    (new Thread() {
      public void run() {
        sendMessage(new Message(UID,h));
      }
    }).start();
  }

/*  void receiveMessages() {
    try {
      while (true) {
        Message m = (Message) ois.readObject();
        switch (m.messageType) {
          case Message.REGISTRATION:
  //          name = (String) m.serverData;
            updateLabel();
     //       setToolTipText("node " + UID + ": " + name);
            break;
          case Message.WAKEUP:
            closeConnection("node sent Wakeup message");
            break;
          case Message.INITIATE:
          case Message.INFORM:
            level = m.level;
            core = m.core;
            updateLabel();
            // don't break
          default:
            forwardMessage(m);
        }
      }
    } catch (Exception e) {
      closeConnection(e);
    }
  } */
  void updateLabel(){
      
  }

/*  void forwardMessage(Message m) {
    if (m.destination == UID)
      sendMessage(m);
    else {
      Node n = (Node) nodes.get(m.destination);
      Edge e = (Edge) neighbors.get(n);
      e.forwardMessage(this,n,m);
    }
  } */

  void closeConnection(Object reason) {
//    this.setEnabled(false);
    if (!terminated) { // print only if not terminated yet
      System.out.println("Closing socket to " + name + " due to: " + reason);
      if (reason instanceof Exception)
        ((Exception) reason).printStackTrace();
    }
//    try {socket.close();} catch (Exception se) {}
    terminated = true;
  }
}
