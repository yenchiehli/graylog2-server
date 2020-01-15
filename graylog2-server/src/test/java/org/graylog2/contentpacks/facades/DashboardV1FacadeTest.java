/**
 * This file is part of Graylog.
 *
 * Graylog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.graylog2.contentpacks.facades;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.google.common.io.Resources;
import com.lordofthejars.nosqlunit.annotation.UsingDataSet;
import com.lordofthejars.nosqlunit.core.LoadStrategyEnum;
import com.lordofthejars.nosqlunit.mongodb.InMemoryMongoDb;
import org.graylog.plugins.views.search.Query;
import org.graylog.plugins.views.search.Search;
import org.graylog.plugins.views.search.SearchRequirements;
import org.graylog.plugins.views.search.db.SearchDbService;
import org.graylog.plugins.views.search.filter.OrFilter;
import org.graylog.plugins.views.search.filter.QueryStringFilter;
import org.graylog.plugins.views.search.filter.StreamFilter;
import org.graylog.plugins.views.search.views.ViewDTO;
import org.graylog.plugins.views.search.views.ViewRequirements;
import org.graylog.plugins.views.search.views.ViewService;
import org.graylog.plugins.views.search.views.ViewStateDTO;
import org.graylog.plugins.views.search.views.widgets.aggregation.AggregationConfigDTO;
import org.graylog.plugins.views.search.views.widgets.aggregation.AutoIntervalDTO;
import org.graylog.plugins.views.search.views.widgets.aggregation.BarVisualizationConfigDTO;
import org.graylog.plugins.views.search.views.widgets.aggregation.LineVisualizationConfigDTO;
import org.graylog.plugins.views.search.views.widgets.aggregation.NumberVisualizationConfigDTO;
import org.graylog.plugins.views.search.views.widgets.aggregation.TimeHistogramConfigDTO;
import org.graylog.plugins.views.search.views.widgets.aggregation.ValueConfigDTO;
import org.graylog.plugins.views.search.views.widgets.aggregation.sort.PivotSortConfig;
import org.graylog.plugins.views.search.views.widgets.messagelist.MessageListConfigDTO;
import org.graylog2.bindings.providers.MongoJackObjectMapperProvider;
import org.graylog2.contentpacks.facades.dashboardV1.DashboardV1Facade;
import org.graylog2.contentpacks.facades.dashboardV1.RandomUUIDProvider;
import org.graylog2.contentpacks.model.ContentPack;
import org.graylog2.contentpacks.model.ContentPackV1;
import org.graylog2.contentpacks.model.entities.Entity;
import org.graylog2.contentpacks.model.entities.NativeEntity;
import org.graylog2.database.MongoConnection;
import org.graylog2.database.MongoConnectionRule;
import org.graylog2.database.NotFoundException;
import org.graylog2.plugin.cluster.ClusterConfigService;
import org.graylog2.shared.bindings.providers.ObjectMapperProvider;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;

import java.net.URL;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;

import static com.lordofthejars.nosqlunit.mongodb.InMemoryMongoDb.InMemoryMongoRuleBuilder.newInMemoryMongoDbRule;
import static org.assertj.core.api.Assertions.assertThat;

public class DashboardV1FacadeTest {

    @ClassRule
    public static final InMemoryMongoDb IN_MEMORY_MONGO_DB = newInMemoryMongoDbRule().build();
    @Rule
    public final MongoConnectionRule mongoRule = MongoConnectionRule.build("test");

    private final ObjectMapper objectMapper = new ObjectMapperProvider().get();

    public static class TestSearchDBService extends SearchDbService {
        protected TestSearchDBService(MongoConnection mongoConnection,
                                      MongoJackObjectMapperProvider mapper) {
            super(mongoConnection, mapper, dto -> new SearchRequirements(Collections.emptySet(), dto));
        }
    }

    public static class TestViewService extends ViewService {
        protected TestViewService(MongoConnection mongoConnection,
                                  MongoJackObjectMapperProvider mapper,
                                  ClusterConfigService clusterConfigService) {
            super(mongoConnection, mapper, clusterConfigService,
                    dto -> new ViewRequirements(Collections.emptySet(), dto));
        }
    }

    private DashboardV1Facade facade;
    private ViewFacadeTest.TestViewService viewService;
    private ViewFacadeTest.TestSearchDBService searchDbService;
    private final String viewId = "5def958063303ae5f68eccae"; /* stored in database */
    private final String newViewId = "5def958063303ae5f68edead";
    private final String newStreamId = "5def958063303ae5f68ebeaf";
    private final String streamId = "5cdab2293d27467fbe9e8a72"; /* stored in database */


    @Before
    public void setUp() {
        objectMapper.registerSubtypes(new NamedType(AggregationConfigDTO.class, AggregationConfigDTO.NAME));
        objectMapper.registerSubtypes(new NamedType(MessageListConfigDTO.class, MessageListConfigDTO.NAME));
        objectMapper.registerSubtypes(new NamedType(LineVisualizationConfigDTO.class, LineVisualizationConfigDTO.NAME));
        objectMapper.registerSubtypes(new NamedType(BarVisualizationConfigDTO.class, BarVisualizationConfigDTO.NAME));
        objectMapper.registerSubtypes(new NamedType(NumberVisualizationConfigDTO.class, NumberVisualizationConfigDTO.NAME));
        objectMapper.registerSubtypes(new NamedType(TimeHistogramConfigDTO.class, TimeHistogramConfigDTO.NAME));
        objectMapper.registerSubtypes(new NamedType(ValueConfigDTO.class, ValueConfigDTO.NAME));
        objectMapper.registerSubtypes(new NamedType(PivotSortConfig.class, PivotSortConfig.Type));
        objectMapper.registerSubtypes(new NamedType(OrFilter.class, OrFilter.NAME));
        objectMapper.registerSubtypes(new NamedType(StreamFilter.class, StreamFilter.NAME));
        objectMapper.registerSubtypes(new NamedType(QueryStringFilter.class, QueryStringFilter.NAME));
        objectMapper.registerSubtypes(new NamedType(AutoIntervalDTO.class, AutoIntervalDTO.type));
        searchDbService = new ViewFacadeTest.TestSearchDBService(mongoRule.getMongoConnection(),
                new MongoJackObjectMapperProvider(objectMapper));
        viewService = new ViewFacadeTest.TestViewService(mongoRule.getMongoConnection(),
                new MongoJackObjectMapperProvider(objectMapper), null);
        final RandomUUIDProvider randomUUIDProvider = new RandomUUIDProvider(new Date(1575020937839L), 1575020937839L);
        facade = new DashboardV1Facade(objectMapper, searchDbService, randomUUIDProvider, viewService);
    }

    @Test
    @UsingDataSet(loadStrategy = LoadStrategyEnum.CLEAN_INSERT)
    public void createNativeEntity() throws Exception {
        final URL resourceUrl = Resources.getResource(DashboardV1Facade.class, "content-pack-dashboard-v1.json");
        final ContentPack contentPack = objectMapper.readValue(resourceUrl, ContentPack.class);
        assertThat(contentPack).isInstanceOf(ContentPackV1.class);
        final ContentPackV1 contentPackV1 = (ContentPackV1) contentPack;
        final Entity entity = contentPackV1.entities().iterator().next();
        final NativeEntity<ViewDTO> nativeEntity = facade.createNativeEntity(entity,
                ImmutableMap.of(), ImmutableMap.of(), "kmerz");
        assertThat(nativeEntity).isNotNull();

        final ViewDTO viewDTO = nativeEntity.entity();
        assertThat(viewDTO).isNotNull();
        assertThat(viewDTO.title()).matches("ContentPack Dashboard");
        assertThat(viewDTO.description()).matches("A dashboard for content packs");
        assertThat(viewDTO.summary()).matches("Converted Dashboard");
        assertThat(viewDTO.type()).isEqualByComparingTo(ViewDTO.Type.DASHBOARD);
        assertThat(viewDTO.state()).isNotNull();
        assertThat(viewDTO.state().size()).isEqualTo(1);
        ViewStateDTO viewState = viewDTO.state().values().iterator().next();
        assertThat(viewState.widgets().size()).isEqualTo(12);

        Optional<Search> optionalSearch = searchDbService.get(viewDTO.searchId());
        Search search = optionalSearch.orElseThrow(NotFoundException::new);
        assertThat(search.queries().size()).isEqualTo(1);
        Query query = search.queries().iterator().next();
        assertThat(query.searchTypes().size()).isEqualTo(15);
    }
}
