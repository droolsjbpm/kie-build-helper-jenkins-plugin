/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kie.jenkinsci.plugins.kieprbuildshelper;

import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.Test;

public class KieRepositoryListsTest {

    @Test
    public void fetchRepositoryListForMaster() {
        List<KieGitHubRepository> kieRepos = KieRepositoryLists.getListForBranch("drools",
                                                                                 "master").getList();
        // don't do any specific assertions as the repo list may change at any time as the test would then start failing
        // at least the manually added errai, uf and dashbuilder repos need to present though
        Assertions.assertThat(kieRepos.size()).isGreaterThan(3);
        Assertions.assertThat(kieRepos).contains(
                new KieGitHubRepository("errai", "errai"),
                new KieGitHubRepository("uberfire", "uberfire"),
                new KieGitHubRepository("dashbuilder", "dashbuilder")
        );
    }

    @Test
    public void fetchRepositoryListFor65x() {
        List<KieGitHubRepository> kieRepos = KieRepositoryLists.getListForBranch("drools",
                                                                                 "6.5.x").getList();
        // don't do any specific assertions as the repo list may change at any time as the test would then start failing
        // at least the manually added errai, uf and dashbuilder repos need to present though
        Assertions.assertThat(kieRepos.size()).isGreaterThan(3);
        Assertions.assertThat(kieRepos).contains(
                new KieGitHubRepository("uberfire", "uberfire"),
                new KieGitHubRepository("uberfire", "uberfire-extensions"),
                new KieGitHubRepository("dashbuilder", "dashbuilder"),
                new KieGitHubRepository("jboss-integration", "kie-eap-modules")
        );
    }

    @Test (expected = IllegalArgumentException.class)
    public void fetchRepositoryListForUnknownBranch() {
        KieRepositoryLists.getListForBranch("drools",
                                            "unknown-branch");
    }

    @Test(expected = RuntimeException.class)
    public void fetchRepositoryFromNonExistingUrl() {
        KieRepositoryLists.fetchRepositoryList("https://github.com/non-existing-url-for.testing", "test");
    }

}
