/**
 * The frame pipeline: table, snapshot, Holds, guards, flush, the first-layout helper and the layer
 * table (ADR 0028). Lowest layer: imports nothing app-level. Rules are enforced by
 * {@code WindowSourceRulesTest}; this package has no allow-list entries.
 */
package com.termux.app.window;
