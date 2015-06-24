package com.versioneye.mojo;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.versioneye.maven.MavenIndexer;
import com.versioneye.service.RabbitMqService;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.index.context.IndexUtils;
import org.apache.maven.index.context.IndexingContext;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import versioneye.domain.MavenRepository;
import versioneye.service.ProductService;

import java.util.Properties;
import java.util.concurrent.*;


/**
 * Fetches the index from the maven central repository and walks through the index
 * to update the database.
 */
@Mojo( name = "central", defaultPhase = LifecyclePhase.PROCESS_SOURCES )
public class CentralMojo extends SuperMojo {

    protected ProductService productService;

    protected final static String QUEUE_NAME = "maven_index_worker";
    protected Connection connection;
    protected Channel channel;


    public void execute() throws MojoExecutionException, MojoFailureException {
        System.out.println("execute");
        try{
            super.execute();
            ApplicationContext context = new ClassPathXmlApplicationContext("applicationContext.xml");
            productService = (ProductService) context.getBean("productService");

            MavenRepository typesafeRepo = mavenRepositoryDao.findByName("typesafe");
            addRepo(typesafeRepo);

            initMavenRepository();

            doUpdateFromIndex();
        } catch( Exception exception ){
            getLog().error(exception);
            throw new MojoExecutionException("Oh no! Something went wrong. Get in touch with the VersionEye guys and give them feedback.", exception);
        }
    }


    protected void doUpdateFromIndex() {
        try{
            initTheRabbit();

            String centralCache = getCacheDirectory(mavenRepository.getName());
            String centralIndex = getIndexDirectory(mavenRepository.getName());

            MavenIndexer mavenIndexer = new MavenIndexer();
            mavenIndexer.initCentralContext(mavenRepository.getUrl(), centralCache, centralIndex);
            mavenIndexer.updateIndex(mavenRepository.getUsername(), mavenRepository.getPassword());

            IndexingContext context = mavenIndexer.getCentralContext();
            IndexSearcher searcher  = context.acquireIndexSearcher();
            final IndexReader ir    = searcher.getIndexReader();
            for ( int i = 0; i < ir.maxDoc(); i++ ) {
                processArtifact(context, ir, i);
            }
            context.releaseIndexSearcher(searcher);

            mavenIndexer.closeIndexer();

            closeTheRabbit();
        } catch (Exception ex){
            getLog().error(ex);
            getLog().error("ERROR in doUpdateFromIndex" + ex.getMessage());
        }
    }


    protected void processArtifact(IndexingContext context, IndexReader indexReader, int i) {
        try {
            if ( indexReader.isDeleted( i ) )
                return ;

            final Document doc = indexReader.document( i );
            final ArtifactInfo artifactInfo = IndexUtils.constructArtifactInfo( doc, context );
            if (artifactInfo == null || artifactInfo.groupId == null || artifactInfo.artifactId == null){
                return ;
            }

            if (skipKnown && productDao.doesVersionExistAlreadyByGA(artifactInfo.groupId.toLowerCase(), artifactInfo.artifactId.toLowerCase(), artifactInfo.version)){
                return ;
            }

            processArtifact(artifactInfo);
        } catch (Exception ex) {
            getLog().error(ex);
        }
    }


    protected void processArtifact(ArtifactInfo artifactInfo) {
        ExecutorService executor = Executors.newCachedThreadPool();
        Callable<Object> task = new MyCallable(artifactInfo);

        Future<Object> future = executor.submit(task);
        try {
            future.get(30, TimeUnit.MINUTES);
        } catch (TimeoutException ex) {
            getLog().error("Timeout Exception for " + artifactInfo.groupId + ":" + artifactInfo.artifactId + ":" + artifactInfo.version + " - " + ex);
            getLog().error(ex);
        } catch (InterruptedException ex) {
            getLog().error("Interrupted Exception: " + artifactInfo.groupId + ":" + artifactInfo.artifactId + ":" + artifactInfo.version + " - " + ex);
            getLog().error(ex);
        } catch (ExecutionException ex) {
            getLog().error("Execution Exception: " + artifactInfo.groupId + ":" + artifactInfo.artifactId + ":" + artifactInfo.version + " - " + ex);
            getLog().error(ex);
        } finally {
            future.cancel(true);
        }
    }


    public class MyCallable implements Callable<Object> {

        private ArtifactInfo artifactInfo;

        public MyCallable (ArtifactInfo artifactInf) {
            artifactInfo = artifactInf;
        }

        public Object call() throws Exception {
            getLog().info("send " + artifactInfo.groupId + ":" + artifactInfo.artifactId + ":" + artifactInfo.version);
            String gav = artifactInfo.groupId + ":" + artifactInfo.artifactId + ":pom:" + artifactInfo.version;
            sendGav(gav, artifactInfo.lastModified);
            return null;
        }
    }


    protected void sendGav(String gav, long lastModified){
        try{
            String message = mavenRepository.getName() + "::" + mavenRepository.getUrl() + "::" + gav + "::" + lastModified;
            channel.queueDeclare(QUEUE_NAME, false, false, false, null);
            channel.basicPublish("", QUEUE_NAME, null, message.getBytes());
            getLog().info(" [x] Sent '" + message + "'");
        } catch (Exception exception) {
            getLog().error("urlToPom: " + gav);
            getLog().error(exception);
        }
    }


    protected void initTheRabbit(){
        try {
            String rabbitmqAddr = System.getenv("RM_PORT_5672_TCP_ADDR");
            String rabbitmqPort = System.getenv("RM_PORT_5672_TCP_PORT");
            if (rabbitmqAddr == null || rabbitmqAddr.isEmpty() || rabbitmqPort == null || rabbitmqPort.isEmpty()){
                Properties properties = getProperties();
                rabbitmqAddr = properties.getProperty("rabbitmq_addr");
                rabbitmqPort = properties.getProperty("rabbitmq_port");
            }
            connection = RabbitMqService.getConnection(rabbitmqAddr, new Integer(rabbitmqPort));
            channel = connection.createChannel();
            getLog().info("Connected to RabbitMQ " + rabbitmqAddr + ":" + rabbitmqPort);
        } catch (Exception exception){
            getLog().error(exception);
        }
    }


    protected void closeTheRabbit(){
        try{
            channel.close();
            connection.close();
            getLog().info("Connection to RabbitMQ closed.");
        } catch (Exception exception){
            getLog().error(exception);
        }
    }


    private void initMavenRepository(){
        mavenRepository = mavenRepositoryDao.findByName("central");
        if (mavenRepository == null){
            mavenRepository = new MavenRepository();
            mavenRepository.setId("central");
            mavenRepository.setName("central");
            mavenRepository.setUrl("http://repo.maven.apache.org/maven2");
            mavenRepository.setLanguage("Java");
        }
    }


}
