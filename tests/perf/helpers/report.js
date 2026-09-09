/**
 * Shared K6 report helper — generates HTML + JSON output.
 *
 * Provides a reusable handleSummary function that all scenarios can import.
 * Outputs:
 *   - HTML report: tests/perf/reports/<scenarioName>_<timestamp>.html
 *   - JSON report: tests/perf/reports/<scenarioName>_<timestamp>.json
 *   - Console text summary (stdout)
 *
 * Usage:
 *   import { buildHandleSummary } from '../../helpers/report.js';
 *   export const handleSummary = buildHandleSummary('auth_p0');
 *
 * @module helpers/report
 */

import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';
import { htmlReport } from 'https://raw.githubusercontent.com/benc-uk/k6-reporter/main/dist/bundle.js';

/**
 * Build a handleSummary function for the given scenario name.
 *
 * @param {string} scenarioName - Identifier used in output filenames (e.g. 'auth_p0', 'registration_chain')
 * @returns {function} A K6-compatible handleSummary function
 */
export function buildHandleSummary(scenarioName) {
  return function (data) {
    const timestamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
    const baseName = `${scenarioName}_${timestamp}`;

    return {
      stdout: textSummary(data, { indent: '  ', enableColors: true }),
      [`/scripts/reports/${baseName}.html`]: htmlReport(data, { title: `K6 Report — ${scenarioName}` }),
      [`/scripts/reports/${baseName}.json`]: JSON.stringify(data, null, 2),
    };
  };
}
