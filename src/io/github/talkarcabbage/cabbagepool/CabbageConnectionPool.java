package io.github.talkarcabbage.cabbagepool;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CabbageConnectionPool {
	
	private static final Logger logger = Logger.getLogger("CommandLoader");
	private static ArrayBlockingQueue<PooledConnection> poolQueue;
	static boolean initialized = false;
	private static String protocol = "";
	private static Properties properties = null;
	
	/**
	 * Initializes the connection pool, filling the queue with connections.
	 * Properties may be null. Depending on if properties is null, 
	 * the {@link DriverManager#getConnection(String)} or 
	 * {@link DriverManager#getConnection(String, properties)} 
	 * method will be used. 
	 * @param size The number of connections to create. There will always be exactly this many connections unless they are erroneously closed.
	 * @param initProtocol The url String as used by the DriverManager
	 * @param A Properties object to use to create the connections. May be null, see above. 
	 * @throws SQLException If the connection attempts throw a SQLException or otherwise fail to provide a connection.
	 * @throws IllegalStateException If there is an error pooling connections into the Queue.
	 */
	public static void init(int size, String initProtocol, Properties initProperties) throws SQLException {
		if (initialized) {
			logger.warning("Tried to initialize the connection pool more than once!");
			return;
		}
		if (!poolQueue.isEmpty()) {
			throw new IllegalStateException("There are already elements inside the pool queue during initialization!");
		}
		
		poolQueue = new ArrayBlockingQueue<>(size);
		protocol = initProtocol;
		properties = initProperties;
		initialized=true;
		
		try {
			while (poolQueue.remainingCapacity()>0) {
				poolQueue.add(new PooledConnection(createNewConnection())); //NOSONAR this is a connection pool. It's not closed on purpose.
			}
		} catch (IllegalStateException e) {
			throw new IllegalStateException("Collections-related exception while adding to the connection pool: ", e);
		}
	}
	
	public static boolean isInitialized() {
		return initialized;
	}
	
	/**
	 * Returns a pooled connection, or null if none are available.
	 * PooledConnection objects should be Closed() via the {@link AutoCloseable} interface, which
	 * will return them to the pool.
	 * @return A PooledConnection object from the pool, or null if unavailable.
	 */
	public static PooledConnection getConnection() {
		return poolQueue.poll();
	}
	
	/**
	 * Returns a pooled connection. Will block until one is available, possibly indefinitely.
	 * PooledConnection objects should be Closed() via the {@link AutoCloseable} interface, which
	 * will return them to the pool.
	 * @return A PooledConnection object.
	 * @throws InterruptedException If the thread is interrupted while awaiting a pool object.
	 */
	public static PooledConnection getConnectionWait() throws InterruptedException {
		try {
			return poolQueue.take();
		} catch (InterruptedException e) {
			logger.log(Level.WARNING, "Interrupted while waiting for connection queue: ");
			throw e;
		}
	}
	
	/**
	 * Returns a pooled connection. Will block until one is available or until the specified millisecond timeout.
	 * PooledConnection objects should be Closed() via the {@link AutoCloseable} interface, which
	 * will return them to the pool.
	 * @return A PooledConnection object, or null.
	 * @throws InterruptedException If the thread is interrupted while awaiting a pool object.
	 */
	public static PooledConnection getConnectionWait(long timeout) throws InterruptedException {
		try {
			return poolQueue.poll(timeout, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			logger.log(Level.WARNING, "Interrupted while waiting for connection queue: ");
			throw e;
		}
	}
	
	public static class PooledConnection implements AutoCloseable {
		Connection connection;
		boolean isInUse;
		boolean destroyed = false;
		
		public PooledConnection(Connection connection) throws SQLException {
			if (connection == null) {
				throw new SQLException("Tried to add a null connection to the pool.");
			}
			this.connection = connection;
		}
		
		/**
		 * Marks the pool as in-use or not.
		 * Does not need to be called normally unless modifying this connection before 
		 * use elsewhere.
		 */
		public void markInUse(boolean inUse) {
			this.isInUse = inUse;
		}
		
		/**
		 * Returns the connection this PooledConnection contains.
		 * @return
		 * @throws SQLException If there is an exception while verifying the status of or recreating the Connection.
		 */
		public Connection getConnection() throws SQLException {
			if (isInUse) {
				logger.warning("A pooled connection is being accessed while already in use!");
			}
			markInUse(true);
			if (connection == null || connection.isClosed()) {
				logger.log(Level.WARNING, "A pooled connection's containing connection was closed or null!");
				this.connection = refreshConnection();
			} 
			return connection;
		}

		@Override
		public void close() {
			if (!isInUse) {
				logger.log(Level.WARNING, "close() was called on a pooled connection that is already closed!");
			}
			markInUse(false);
			
			try {
				if (connection.isClosed()) {
					logger.log(Level.WARNING, "A pooled connection's containing connection was closed!");
					this.connection = refreshConnection();
				} 
				if (!destroyed) {
					CabbageConnectionPool.poolQueue.add(this);
				}
			} catch (IllegalStateException e) {
				logger.log(Level.SEVERE, "There was not enough space in the connection pool to re-add a connection! ", e);
			} catch (SQLException e) {
				logger.log(Level.SEVERE, "Exception while verifying or recreating connection status: ", e);
			}
		}
		
		/**
		 * Called if the connection attached to this pool is closed and needs
		 * to be recreated.
		 * @return
		 * @throws SQLException 
		 */
		private Connection refreshConnection() throws SQLException {
			return createNewConnection();
		}
		
		/**
		 * Closes the connection and marks the object as no longer able to be returned to the queue.
		 * Only for use in connection errors.
		 */
		public void destroy() {
			try {
				destroyed=true;
				connection.close();
			} catch (SQLException e) {
				// No code necessary if this fails.
			}
		}
		
		@Override
		public void finalize() throws Throwable { //Better than nothing if we somehow get GC'd without being readded to the list.
			super.finalize();
			connection.close();
		}
	}
	
	/**
	 * Creates a new connection for use in the connection pool and returns it.
	 */
	private static Connection createNewConnection() throws SQLException {
		if (properties == null)
			return DriverManager.getConnection(protocol);
		else
			return DriverManager.getConnection(protocol, properties);
	}

	private CabbageConnectionPool() {}
}
