package com.versioneye.mojo;


import com.versioneye.persistence.IArtefactDao;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import com.versioneye.domain.GlobalSetting;
import com.versioneye.domain.MavenRepository;
import com.versioneye.domain.Repository;
import com.versioneye.persistence.IGlobalSettingDao;
import com.versioneye.persistence.IMavenRepostoryDao;
import com.versioneye.persistence.IProductDao;
import com.versioneye.service.ProductService;

/*
    This Mojo is used in VersionEye Enterprise to crawl internal repositories.
 */
@Mojo( name = "repo1index", defaultPhase = LifecyclePhase.PROCESS_SOURCES )
public class Repo1IndexMojo extends CentralMojo {

    static final Logger logger = LogManager.getLogger(Repo1IndexMojo.class.getName());

    private String username;
    private String password;

    public void execute() throws MojoExecutionException, MojoFailureException {
        try{
            ApplicationContext context = new ClassPathXmlApplicationContext("applicationContext.xml");

            productDao         = (IProductDao)        context.getBean("productDao");
            mavenRepositoryDao = (IMavenRepostoryDao) context.getBean("mavenRepositoryDao");
            globalSettingDao   = (IGlobalSettingDao)  context.getBean("globalSettingDao");

            productService = (ProductService) context.getBean("productService");
            artefactDao    = (IArtefactDao) context.getBean("artefactDao");

            String env = System.getenv("RAILS_ENV");
            GlobalSetting gs = globalSettingDao.getBy(env, "mvn_repo_1_type");
            if (gs == null || !gs.getValue().equals("maven_index")){
                logger.info("Skip repo1index because mvn_repo_1_type is not maven_index");
                return ;
            }

            fetchUserAndPassword();
            String baseUrl = fetchBaseUrl();

            if (!baseUrl.contains(";")) {
                crawlUrl(baseUrl, 0);
            } else {
                int z = 0;
                String[] splits = baseUrl.split(";");
                for (String url : splits){
                    crawlUrl(url, z);
                    z += 1;
                }
            }
        } catch( Exception exception ){
            logger.error(exception);
            throw new MojoExecutionException("Oh no! Something went wrong. Get in touch with the VersionEye guys and give them feedback.", exception);
        }
    }

    private void crawlUrl(String baseUrl, int count){
        try{
            String name = "MavenInternal" + count;
            logger.info("-- Starting to crawl " + name + " with base Url: " + baseUrl);

            mavenRepository = new MavenRepository();
            mavenRepository.setName(name);
            mavenRepository.setUrl(baseUrl);
            mavenRepository.setUsername(username);
            mavenRepository.setPassword(password);
            mavenRepository.setLanguage("Java");

            addRepo(mavenRepository);

            super.doUpdateFromIndex();
        } catch (Exception ex){
            logger.error(ex);
        }
    }

    private String fetchBaseUrl(){
        String env = System.getenv("RAILS_ENV");
        logger.info("fetchBaseUrl for env: " + env );
        try{
            GlobalSetting gs = globalSettingDao.getBy(env, "mvn_repo_1");
            String url = gs.getValue();
            logger.info(" - mvn_repo_1: " + url);
            return url;
        } catch( Exception ex){
            ex.printStackTrace();
            return "http://repo.maven.apache.org/maven2";
        }
    }

    private void fetchUserAndPassword(){
        String env = System.getenv("RAILS_ENV");
        try{
            username = globalSettingDao.getBy(env, "mvn_repo_1_user").getValue();
            password = globalSettingDao.getBy(env, "mvn_repo_1_password").getValue();
        } catch( Exception ex){
            ex.printStackTrace();
            username = null;
            password = null;
        }
    }

}
