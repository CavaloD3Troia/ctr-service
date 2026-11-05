/**
 * Deliberate violations of the hexagonal boundary rules, one minimal class per rule, plus one clean "resident" per
 * layer. {@code ArchitectureRulesTest} checks that every rule rejects its fixture. The sub-packages mirror the layers
 * under a root of their own, and the classes are test classes, so the production {@code ArchitectureTest} (which
 * imports {@code org.cavalodetroia.ctr} without tests) never sees them.
 */
package org.cavalodetroia.ctr.archfixtures;
