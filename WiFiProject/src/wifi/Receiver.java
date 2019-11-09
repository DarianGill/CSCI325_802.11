package wifi;

/**
 * Receiver will monitor the network for incoming data, which it will remove from it's packet and store in 
 * an ArrayBlockingQueue until the data is requested by the client layer. This class is also responsible for
 * handling ACKs after receiving data.
 * 
 * @author Kyle Muir
 * @version 11.3.19
 *
 */
public class Receiver implements Runnable {
	
	/**
	 * 
	 * 
	 */
	@Override
	public void run() {
		//TODO
	}

}
