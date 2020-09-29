# What

This application is an HTTP proxy server. Specifically, it implements (a very minimal version of) the CONNECT [1] specification.

[1] https://tools.ietf.org/html/rfc7231#section-4.3.6

# How to use

To compile the application:

    mvn package

To run the application:

    java -jar target/HttpProxy-1.0.jar <sourceHostname> <sourcePort> <targetHostname> <targetPort> <maxConnections>

For example:

    java -jar target/HttpProxy-1.0.jar 127.0.0.1 9000 example.com 443 10000

Once you have the server running, try testing it with:

    curl -s --proxytunnel --proxy "http://127.0.0.1:9000" https://example.com

To check the health of the proxy server:

    curl -s http://127.0.0.1:9000/health

The health check works as follows:
 - If the `maxConnections` limit has been reached, it returns `204 No Content`
 - Otherwise, it returns `200 OK`, along with a number which indicates how many more concurrent connections it can handle.

# Design Notes

This application is a single-threaded TCP server which relies on `java.nio`, which allows it to efficiently support a high number of
concurrent connections (due to the framework's use of `kqueue`/`epoll`.)

The main idea is simple:

    - Listen on TCP endpoint [sourceHostname]:[sourcePort]
    - For each connection:
        - Attempt to read all HTTP headers. (Really, the headers are ignored, except for the HTTP action/verb.)
        - If we receive an HTTP "GET /health", simply return a health check report
        - If we receive an HTTP "CONNECT example.com:443" action from a client, then...
            - Establish a new connection to [targetHostname]:[targetPort]
            - Start reading from the client, and writing to the new target host connection
            - Start reading from the target host connection, and writing to client
            - If both ends close their connections, free all resources

Due to the use of `java.nio`, all of this happens asynchronously. (The side effect being that the code is slightly convoluted.)

# Performance Notes

This application:

- Is fast/efficient (...it uses java.nio to multiplex high numbers of connections at once)
- Is scalable (...it's essentially stateless between connections)

Clients who use the service to send repeated requests should keep connections open for re-use; especially for HTTPS.

To run perf tests, see [perf_test.py](src/test/py/perf_test.py).  On a single node, this easily runs 1k+ concurrent connections
before encountering failures. Given the choice of using `java.nio`, I suspect that, with some tuning (e.g., JVM heap 
size, process ulimits, etc) this can be improved to 10k+ connections. 

TODO: Deploy the app to a cloud that supports autoscaling, and performance test. Configure the load balancing policy to monitor the health check endpoint, and only forward new connections to nodes passing the health check. In theory, since the health check endpoint returns a number, an advanced load balancing algorithm could be configured to choose the server with the lowest number of active connections.

# TODO

There is a lot of room for improvement:

- Clean up in general (Due to its asynchronous nature, code is not as readable as it could be.)
- Add unit tests (Sorry, this project was slapped together pretty quickly using `curl` to test.)
- Perform more robust perf test (test with increased JVM heap, fd limits, etc)
- Audit for resource leaks (most likely there are edge cases that cause resource leaks)
- Tighten timeouts/DoS-vectors (right now we're pretty trusting of the client)
- Support multiple target endpoints (right now we only allow one hostname:port pair)
- Add authentication (for example, require an OAuth token)
- Fix health endpoint bug (sometimes under high load it lags / doesn't return a value; run w/ separate port/thread?)
