/**
 * Deliberate violations of the hexagonal boundary rules, one minimal class per rule, plus one clean "resident" per
 * layer and per named package. {@code ArchitectureRulesTest} checks that every rule rejects its fixture and that no
 * other rule does. The sub-packages mirror the kind-first layout under a root of their own, and the classes are test
 * classes, so the production check (which imports {@code org.cavalodetroia.ctr} without tests) never sees them.
 */
package org.cavalodetroia.ctr.archfixtures;
