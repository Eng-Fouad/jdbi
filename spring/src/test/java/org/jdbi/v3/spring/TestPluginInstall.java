/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jdbi.v3.spring;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.spi.JdbiPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestPluginInstall.Config.class)
public class TestPluginInstall {

    @Autowired
    private Config config;
    @Autowired
    private Jdbi jdbi;

    @Test
    public void testPluginsInstalled() {
        assertThat(jdbi).isNotNull();
        assertThat(config.pluginACalled).isTrue();
        assertThat(config.pluginBCalled).isTrue();
    }

    @Configuration
    public static class Config {

        boolean pluginACalled, pluginBCalled;

        @Bean
        public JdbiFactoryBean jdbiFactory() {
            return new JdbiFactoryBean().setDataSource(new DriverManagerDataSource());
        }

        @Bean
        public JdbiPlugin pluginA() {
            return new JdbiPlugin() {
                @Override
                public void customizeJdbi(final Jdbi db) {
                    pluginACalled = true;
                }
            };
        }

        @Bean
        public JdbiPlugin pluginB() {
            return new JdbiPlugin() {
                @Override
                public void customizeJdbi(final Jdbi db) {
                    pluginBCalled = true;
                }
            };
        }
    }
}
