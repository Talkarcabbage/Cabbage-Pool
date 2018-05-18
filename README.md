# Cabbage-Pool
A very small, very simple connection pooling library for JDBC connection pooling aimed at smaller projects.

# Usage
Include in classpath.

Use `CabbageConnectionPool.init(int numberOfConnections, String url, Properties properties)` to initialize the connection pool. Throws SQLException if the connection creation failed for any connections in the pool.
numberOfConnections - The (static) number of connections to create.
url - The url to use to create the connection. `Used by DriverManager.getConnection(protocol)` or `DriverManager.getConnection(protocol, properties)` depending on whether the Properties argument is null.
Properties - (optional, can be null) Properties used via `DriverManager.getConnection(protocol, properties)`

Call one of the methods `getConnection()` `getConnectionWait()` or `getConnectionWait(timeOutMillis)` to attempt to obtain a PooledConnection.
`getConnection()` and `getConnectionWait(timeOutMillis)` may return null if a connection is not available.

A PooledConnection object's underlying Connection may be accessed via `pooledConnection.getConnection();` and may throw a SQLException if the connection is invalid.

PooledConnection objects are a simple wrapper containing and maintaining a Connection object. The underlying Connection should not be closed, as it will be returned to the pool when the PooledConnection is closed. Any resources such as Statements or ResultSets should be closed, however.
