package com.versioneye.maven;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.maven.index.*;
import org.apache.maven.index.context.IndexCreator;
import org.apache.maven.index.context.IndexUtils;
import org.apache.maven.index.context.IndexingContext;
import org.apache.maven.index.expr.SourcedSearchExpression;
import org.apache.maven.index.updater.*;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.authentication.AuthenticationInfo;
import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.events.TransferListener;
import org.apache.maven.wagon.observers.AbstractTransferListener;
import org.apache.maven.wagon.providers.http.HttpWagon;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.sonatype.aether.version.InvalidVersionSpecificationException;

import java.io.File;
import java.io.IOException;
import java.util.*;

//import org.apache.maven.wagon.providers.http.HttpWagon;

public class MavenIndexer {

    static final Logger logger = LogManager.getLogger(MavenIndexer.class.getName());

    private final PlexusContainer plexusContainer;
    private final Indexer indexer;
    private final IndexUpdater indexUpdater;
    private final Wagon httpWagon;
    private IndexingContext centralContext;


    public MavenIndexer() throws PlexusContainerException, ComponentLookupException {
        this.plexusContainer = new DefaultPlexusContainer();

        this.indexer      = plexusContainer.lookup( Indexer.class );
        this.indexUpdater = plexusContainer.lookup( IndexUpdater.class );
        this.httpWagon    = plexusContainer.lookup( Wagon.class, "http" );

        setHttpHeaders();
    }


    private void setHttpHeaders(){
        Properties properties = new Properties();
        properties.setProperty("User-Agent", "mojo/nb-repository-plugin");
        HttpWagon httpWagon_ = (HttpWagon) httpWagon;
        httpWagon_.setHttpHeaders(properties);
    }


    public void initCentralContext(String repo, String centraCache, String centralIndex) throws IOException, ComponentLookupException, InvalidVersionSpecificationException {
        if (repo == null){
            repo = "http://repo.maven.apache.org/maven2"; // http://repo1.maven.org/maven2
        }
        File centralLocalCache = new File( centraCache );
        File centralIndexDir   = new File( centralIndex );

        List<IndexCreator> indexers = new ArrayList<IndexCreator>();
        indexers.add( plexusContainer.lookup( IndexCreator.class, "min" ) );
        indexers.add( plexusContainer.lookup( IndexCreator.class, "jarContent" ) );
        indexers.add( plexusContainer.lookup( IndexCreator.class, "maven-plugin" ) );

        centralContext = indexer.createIndexingContext( "central-context", "central", centralLocalCache,
                centralIndexDir, repo, null, true, true, indexers );
    }


    public void closeIndexer() throws IOException {
        indexer.closeIndexingContext( centralContext, false );
    }


    /*
     * Update the index (incremental update will happen if this is not 1st run and files are not deleted)
     * This whole block below should not be executed on every app start, but rather controlled by some configuration
     * since this block will always emit at least one HTTP GET. Central indexes are updated once a week, but
     * other index sources might have different index publishing frequency.
     * Preferred frequency is once a week.
     */
    public void updateIndex(String username, String password) throws IOException, ComponentLookupException, InvalidVersionSpecificationException {
        logger.info("Updating Index...");
        logger.info("This might take a while on first run, so please be patient!");
        // Create ResourceFetcher implementation to be used with IndexUpdateRequest
        // Here, we use Wagon based one as shorthand, but all we need is a ResourceFetcher implementation
        TransferListener listener = new AbstractTransferListener() {
            public void transferStarted( TransferEvent transferEvent ) {
                logger.info("  Downloading " + transferEvent.getResource().getName());
            }
            public void transferProgress( TransferEvent transferEvent, byte[] buffer, int length ){ }
            public void transferCompleted( TransferEvent transferEvent ) {
                logger.info(" - Done");
            }
        };

        AuthenticationInfo authInfo = null;
        if (username != null && password != null && !username.trim().isEmpty() && !password.trim().isEmpty()){
            authInfo = new AuthenticationInfo();
            authInfo.setUserName(username);
            authInfo.setPassword(password);
        }

        ResourceFetcher resourceFetcher     = new WagonHelper.WagonFetcher( httpWagon, listener, authInfo, null );
        Date centralContextCurrentTimestamp = centralContext.getTimestamp();
        IndexUpdateRequest updateRequest    = new IndexUpdateRequest( centralContext, resourceFetcher );
        IndexUpdateResult updateResult      = indexUpdater.fetchAndUpdateIndex( updateRequest );
        if ( updateResult.isFullUpdate() ) {
            logger.info("Full update happened!");
        }
        else if ( updateResult.getTimestamp().equals( centralContextCurrentTimestamp ) ) {
            logger.info("No update needed, index is up to date!");
        }
        else {
            logger.info("Incremental update happened, change covered " + centralContextCurrentTimestamp
                    + " - " + updateResult.getTimestamp() + " period.");
        }
    }

    public IteratorSearchResponse executeGroupArtifactSearch(String group, String artifact, String version) throws IOException, ComponentLookupException, InvalidVersionSpecificationException {
        final Query groupIdQ     = indexer.constructQuery( MAVEN.GROUP_ID,    new SourcedSearchExpression( group ) );
        final BooleanQuery query = new BooleanQuery();
        query.add( groupIdQ   , Occur.MUST );

        if (artifact != null && !artifact.trim().isEmpty()){
            final Query artifactIdQ  = indexer.constructQuery( MAVEN.ARTIFACT_ID, new SourcedSearchExpression( artifact ) );
            query.add( artifactIdQ, Occur.MUST );
        }

        if (version != null && !version.trim().isEmpty()){
            final Query versionQ  = indexer.constructQuery( MAVEN.VERSION, new SourcedSearchExpression( version ) );
            query.add( versionQ, Occur.MUST );
        }

//        query.add( indexer.constructQuery( MAVEN.PACKAGING, new SourcedSearchExpression( "pom" ) ), Occur.MUST );

        IteratorSearchRequest  request  = new IteratorSearchRequest( query, Collections.singletonList( centralContext ), null );
        IteratorSearchResponse response = indexer.searchIterator( request );
        return response;
    }

    public void walkThroughIndex() throws IOException {
        final IndexSearcher searcher = centralContext.acquireIndexSearcher();
        try {
            final IndexReader ir = searcher.getIndexReader();
            for ( int i = 0; i < ir.maxDoc(); i++ ) {
                if ( !ir.isDeleted( i ) ) {
                    final Document doc = ir.document( i );
                    final ArtifactInfo ai = IndexUtils.constructArtifactInfo( doc, centralContext );

                    logger.info(ai.groupId + ":" + ai.artifactId + ":" + ai.version + ":" + ai.classifier + "." + ai.fextension + " (sha1=" + ai.sha1 + ")" );
                }
            }
        } finally {
            centralContext.releaseIndexSearcher( searcher );
        }
    }

    public IndexingContext getCentralContext(){
        return centralContext;
    }


}
