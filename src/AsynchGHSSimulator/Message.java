package AsynchGHSSimulator;

/**
* @author Shraddha Patel, Priyanka Menghani
*/
public final class Message implements java.io.Serializable {
  // Message Type options
  public static final int REGISTRATION = 0;
  public static final int WAKEUP = 1;
  public static final int INITIATE = 2;
  public static final int TEST = 3;
  public static final int ACCEPT = 4;
  public static final int REJECT = 5;
  public static final int REPORT = 6;
  public static final int CHANGE_ROOT = 7;
  public static final int CONNECT = 8;
  public static final int INFORM = 9;
  public static final int ALL_DONE = 10;

  public static final String[] messageNames = {"registration",
      "wakeup",
      "initiate",
      "test",
      "accept",
      "reject",
      "report",
      "changeRoot",
      "connect",
      "inform",
      "allDone"};


  public int messageType; //one of the above constants
  public int sender;      //Node ID of sender
  public int destination; //Node ID of destination
  public int core;        //Node ID of sender's fragment core (not always used)
  public int level;       //Node ID of sender's level (not always used)
  public int cost;        //MWOE cost in sender's subtree (not always used)
  public Object data; // used in registration messages
/**
 * constructor to send a copy of edges to all nodes
 * @param destination
 * @param Data 
 */
   public Message(int destination, Object Data) {
    messageType = REGISTRATION;
    this.destination = destination;
    this.data = Data;
  }
/**
 * Constructor for Initiate, Test, Connect, and Inform messages:
 * @param messageType
 * @param sender
 * @param destination
 * @param core
 * @param level 
 */
    public Message(int messageType, int sender, int destination,
                 int core, int level) {
    this(messageType, sender, destination, core, level, 0);
  }
/**
 * Constructor for Report mesages
 * @param messageType
 * @param sender
 * @param destination
 * @param cost 
 */ 
  public Message(int messageType, int sender, int destination, int cost) {
    this(messageType, sender, destination, 0, 0, cost);
  }
/**
 * Constructor for all other mesages
 * @param messageType
 * @param sender
 * @param destination 
 */ 
  public Message(int messageType, int sender, int destination) {
    this(messageType, sender, destination, 0, 0, 0);
  }

  // You probably won't need this constructor:
  public Message(int messageType,
                 int sender,
                 int destination,
                 int core,
                 int level,
                 int cost) {
    if (messageType < 0 || messageType > 10)
      throw new IllegalArgumentException("Bad message type: " + messageType);
    this.messageType = messageType;
    this.sender = sender;
    this.destination = destination;
    this.core = core;
    this.level = level;
    this.cost = cost;
  }

  @Override
  public String toString() {
    return sender + "-->" + destination + ": " + shortString();
  }
/**
 * Method to print message type
 * @return 
 */
  public String shortString() {
    String result = messageNames[messageType] + "(";
    switch (messageType) {
      case REPORT:
        result += cost; break;
      case INITIATE:
      case TEST:
      case CONNECT:
      case INFORM:
        result += core + "," + level; break;
      case REGISTRATION:
        result += data;
    }
    result += ")";
    return result;
  }

}


