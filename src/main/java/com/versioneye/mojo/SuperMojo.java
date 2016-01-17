package com.versioneye.mojo;

import com.versioneye.utils.PropertiesUtils;
import com.versioneye.utils.RepositoryUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.repository.Authentication;
import org.sonatype.aether.repository.RemoteRepository;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import versioneye.domain.MavenRepository;
import versioneye.domain.Repository;
import versioneye.persistence.IGlobalSettingDao;
import versioneye.persistence.IMavenRepostoryDao;
import versioneye.persistence.IProductDao;
import versioneye.utils.HttpUtils;

import java.io.File;
import java.util.List;
import java.util.Properties;

/**
 * The Mother of all Mojos!
 */
public abstract class SuperMojo extends AbstractMojo {

    static final Logger logger = LogManager.getLogger(SuperMojo.class.getName());

    @Component
    protected RepositorySystem system;

    @Component
    protected ProjectBuilder projectBuilder;

    @Parameter(defaultValue = "${localRepository}" )
    protected ArtifactRepository localRepository;

    @Parameter( defaultValue="${project}" )
    protected MavenProject project;

    @Parameter( defaultValue="${repositorySystemSession}" )
    protected RepositorySystemSession session;

    @Parameter( defaultValue = "${project.remoteProjectRepositories}")
    protected List<RemoteRepository> repos;

    @Parameter( defaultValue = "${basedir}", property = "basedir", required = true)
    protected File projectDirectory;

    @Parameter( property = "skipKnown")
    protected Boolean skipKnown = Boolean.TRUE;

    protected HttpUtils httpUtils;
    protected RepositoryUtils repositoryUtils = new RepositoryUtils();
    protected MavenRepository mavenRepository;
    protected Repository repository;
    protected IMavenRepostoryDao mavenRepositoryDao;
    protected IProductDao productDao;
    protected IGlobalSettingDao globalSettingDao;
    protected ApplicationContext context;

    public void execute() throws MojoExecutionException, MojoFailureException {
        try{
            context = new ClassPathXmlApplicationContext("applicationContext.xml");
            mavenRepositoryDao = (IMavenRepostoryDao) context.getBean("mavenRepositoryDao");
            productDao = (IProductDao) context.getBean("productDao");
            globalSettingDao = (IGlobalSettingDao) context.getBean("globalSettingDao");
            httpUtils = (HttpUtils) context.getBean("httpUtils");
        } catch (Exception ex){
            logger.error(ex);
        }
    }


    protected void addRepo(MavenRepository repository){
        if (repository == null){
            return ;
        }
        for (RemoteRepository rr : repos ){
            if (rr.getId().equals(repository.getName())){
                return ;
            }
        }
        RemoteRepository remoteRepository = new RemoteRepository(repository.getName(), "default", repository.getUrl());
        remoteRepository.getPolicy(false).setUpdatePolicy("always");
        if (repository.getUsername() != null && !repository.getUsername().isEmpty() && repository.getPassword() != null && !repository.getPassword().isEmpty()){
            Authentication auth = new Authentication(repository.getUsername(), repository.getPassword());
            remoteRepository.setAuthentication(auth);
        }
        repos.add(remoteRepository);
        for (RemoteRepository repo : repos) {
            repo.getPolicy(false).setUpdatePolicy("always");
        }
        logger.info("There are " + repos.size() + " remote repositories in the list");
    }

    protected void addAllRepos(){
        List<MavenRepository> repositories = mavenRepositoryDao.loadAll();
        for (MavenRepository repository : repositories){
            if (repository.getName().equals("central"))
                continue;
            if (repository.getUrl().equals("http://download.java.net/maven/2/"))
                continue;
            RemoteRepository remoteRepository = new RemoteRepository(repository.getName(), "default", repository.getUrl());
            remoteRepository.getPolicy(false).setUpdatePolicy("always");
            repos.add(remoteRepository);
        }
        logger.info("There are " + repos.size() + " remote repositories in the list");
    }

    protected String getCacheDirectory(String name) throws Exception {
        Properties properties = getProperties();
        String baseDir = properties.getProperty("base_cache");
        File directory = new File(baseDir + "/" + name + "-cache");
        if (directory.exists()){
            directory.delete();
        }
        directory.mkdir();
        logger.info("cache directory for Indexer: " + directory.getAbsolutePath());
        return directory.getAbsolutePath();
    }

    protected String getIndexDirectory(String name) throws Exception {
        Properties properties = getProperties();
        String baseDir = properties.getProperty("base_index");
        File directory = new File(baseDir + "/" + name + "-index");
        if (directory.exists()){
            directory.delete();
        }
        directory.mkdir();
        logger.info("index directory for Indexer: " + directory.getAbsolutePath());
        return directory.getAbsolutePath();
    }

    protected Properties getProperties() throws Exception {
        PropertiesUtils propertiesUtils = new PropertiesUtils();
        String propFile = projectDirectory + "/src/main/resources/settings.properties";

        File file = new File(propFile);
        if (!file.exists())
            throw new MojoExecutionException(propFile + " is missing!");

        return propertiesUtils.readProperties(propFile);
    }

    protected void setRepository(String repoName){
        if (context == null){
            context = new ClassPathXmlApplicationContext("applicationContext.xml");
        }
        repository = (Repository) context.getBean(repoName);
//        mavenProjectProcessor.setRepository(repository);
//        mavenPomProcessor.setRepository(repository);

        mavenRepository = mavenRepositoryDao.findByName(repoName);
        addRepo(mavenRepository);
    }

}
