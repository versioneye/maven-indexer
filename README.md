# maven-indexer

This Maven project is using [org.apache.maven.indexer/indexer-core](https://www.versioneye.com/java/org.apache.maven.indexer:indexer-core/5.1.1) to fetch and read maven indexes. Unfortunately the indexer-core project still relies on Maven 3.0.5. With higher versions of Maven the indexer-core is not running correctly. 

This project is only fetching and reading Maven indexes from different repository servers. The project checks if the artefact is already in the VersionEye DB or not. If the artefact is a new one it sends a message to the RabbitMQ server with the corresponding coordiantes. There are different RabbitMQ workers running on Maven 3.3.X withe Eclipse Aether, fetching & parsing the actual pom file and writing the new artefact to the VersionEye DB. 
