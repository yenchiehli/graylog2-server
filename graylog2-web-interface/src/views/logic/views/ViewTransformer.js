// @flow strict
import { List, Map } from 'immutable';
import Widget from 'views/logic/widgets/Widget';
import type { QueryId } from 'views/logic/queries/Query';
import Query from 'views/logic/queries/Query';

import type { ViewStateMap } from './View';
import View from './View';
import ViewState from './ViewState';

const ViewTransformer = (searchView: View): View => {
  const queryMap: Map<QueryId, Query> = Map(searchView.search.queries.map(q => [q.id, q]));
  const newViewStateMap: ViewStateMap = (searchView.state || Map.of()).map((viewState: ViewState, queryId: string) => {
    const { timerange, query, filter = List.of() } = queryMap.get(queryId);

    const streams = filter.get('filters', List.of())
      .filter(value => Map.isMap(value) && value.get('type') === 'stream')
      .map(value => value.get('id'))
      .toList()
      .toArray();

    const widgets: List<Widget> = viewState.widgets.map((widget: Widget) => {
      return widget.toBuilder()
        .timerange(timerange)
        .query(query)
        .streams(streams)
        .build();
    });
    return viewState.toBuilder()
      .widgets(widgets)
      .build();
  });

  return searchView.toBuilder()
    .newId()
    .type(View.Type.Dashboard)
    .state(newViewStateMap)
    .build();
};

export default ViewTransformer;
