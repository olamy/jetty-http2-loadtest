` mvn clean install -pl :jetty-http2-loadtest-server -am && /Library/Java/JavaVirtualMachines/jdk1.8.0_121.jdk/Contents/Home/bin/java -Dorg.mortbay.jetty.load.server.hp2.LEVEL=DEBUG -Xbootclasspath/p:/Users/olamy/mvn-repoortbay/jetty/alpnlpn-boot/8.1.11.v20170118/alpn-boot-8.1.11.v20170118.jar -jar jetty-http2-loadtest-server/target/jetty-http2-loadtest-server-uber.jar --port 9090 `

` mvn clean install -pl :jetty-http2-loadtest-client -am && /Library/Java/JavaVirtualMachines/jdk1.8.0_121.jdk/Contents/Home/bin/java  -Dorg.mortbay.jetty.load.client.http2.LEVEL=DEBUG -jar  jetty-http2-loadtest-client/target/jetty-http2-loadtest-client-uber.jar -rm 1 `

