import { createGlobalStyle } from 'styled-components';

import { paginationStyles } from 'components/graylog/Pagination.jsx';
import { progressBarStyles } from 'components/graylog/ProgressBar.jsx';

const GlobalThemeStyles = createGlobalStyle`
  ${paginationStyles};
  ${progressBarStyles};
`;

export default GlobalThemeStyles;
