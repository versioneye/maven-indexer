package com.versioneye.utils;

import com.versioneye.domain.MavenRepository;
import com.versioneye.domain.Repository;

/**
 * Created with IntelliJ IDEA.
 * User: robertreiz
 * Date: 8/12/13
 * Time: 11:44 AM
 */
public class RepositoryUtils {

    public Repository convertRepository(MavenRepository mavenRepository){
        if (mavenRepository == null){
            return null;
        }
        Repository repository = new Repository();
        repository.setSrc(mavenRepository.getUrl());
        repository.setName(mavenRepository.getName());
        repository.setLanguage(mavenRepository.getLanguage());
        repository.setRepoType("Maven2");
        return repository;
    }

    public Repository convertRepository(String name, String url, String language){
        if (language == null) {
            language = "Java";
        }
        Repository repository = new Repository();
        repository.setSrc(url);
        repository.setName(name);
        repository.setLanguage(language);
        repository.setRepoType("Maven2");
        return repository;
    }

}
