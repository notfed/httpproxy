import http.client
import threading
import time

numberOfThreads = 1000

def doSession(i):
    conn = http.client.HTTPConnection("127.0.0.1", 9000)
    try:
        conn.set_tunnel("127.0.0.1",port=7777)
        conn.request("GET","/")
        response = conn.getresponse()
        print(i, " -> ", response.status, response.reason)
    except Exception as e:
        print(e)
    finally:
        conn.close()

def spawnSession(i):
    # Create a Thread with a function without any arguments
    th = threading.Thread(target=doSession, args=[i])
    # Start the thread
    th.start()
    return th

# Spawn threads
threads = []
for i in range(numberOfThreads):
    threads.append(spawnSession(i))

# Wait for threads
for th in threads:
    th.join()