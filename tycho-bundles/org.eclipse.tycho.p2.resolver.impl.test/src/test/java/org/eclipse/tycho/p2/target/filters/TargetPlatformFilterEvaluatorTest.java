/*******************************************************************************
 * Copyright (c) 2011, 2012 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.tycho.p2.target.filters;

import static org.eclipse.tycho.artifacts.TargetPlatformFilter.removeAllFilter;
import static org.eclipse.tycho.artifacts.TargetPlatformFilter.restrictionFilter;
import static org.eclipse.tycho.artifacts.TargetPlatformFilter.CapabilityPattern.patternWithVersion;
import static org.eclipse.tycho.artifacts.TargetPlatformFilter.CapabilityPattern.patternWithVersionRange;
import static org.eclipse.tycho.artifacts.TargetPlatformFilter.CapabilityPattern.patternWithoutVersion;
import static org.hamcrest.CoreMatchers.allOf;
import static org.junit.Assert.assertThat;
import static org.junit.matchers.JUnitMatchers.containsString;
import static org.junit.matchers.JUnitMatchers.hasItem;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepository;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepositoryManager;
import org.eclipse.tycho.artifacts.TargetPlatformFilter;
import org.eclipse.tycho.artifacts.TargetPlatformFilter.CapabilityPattern;
import org.eclipse.tycho.artifacts.TargetPlatformFilter.CapabilityType;
import org.eclipse.tycho.artifacts.TargetPlatformFilterSyntaxException;
import org.eclipse.tycho.p2.impl.test.ResourceUtil;
import org.eclipse.tycho.test.util.MemoryLog;
import org.eclipse.tycho.test.util.P2Context;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.internal.matchers.TypeSafeMatcher;

public class TargetPlatformFilterEvaluatorTest {

    private static final CapabilityPattern ALL_MULTIVERSION_BUNDLES = patternWithVersion(CapabilityType.OSGI_BUNDLE,
            "trf.bundle.multiversion", null);

    @Rule
    public P2Context p2Context = new P2Context();

    private Set<IInstallableUnit> baselineUnits;
    private LinkedHashSet<IInstallableUnit> workUnits;

    private TargetPlatformFilterEvaluator subject;

    private MemoryLog logger;

    @Before
    public void setUp() throws Exception {
        baselineUnits = loadTestUnits(); // TODO do this in beforeClass -> requires @ClassRule from junit >= 4.9
        workUnits = new LinkedHashSet<IInstallableUnit>(baselineUnits);

        logger = new MemoryLog();
    }

    private Set<IInstallableUnit> loadTestUnits() throws Exception {
        IMetadataRepositoryManager metadataManager = (IMetadataRepositoryManager) p2Context.getAgent().getService(
                IMetadataRepositoryManager.SERVICE_NAME);
        File testDataFile = ResourceUtil.resourceFile("targetfiltering/content.xml");
        IMetadataRepository testDataRepository = metadataManager.loadRepository(testDataFile.getParentFile().toURI(),
                null);
        return testDataRepository.query(QueryUtil.ALL_UNITS, null).toUnmodifiableSet();
    }

    private TargetPlatformFilterEvaluator newEvaluator(List<TargetPlatformFilter> filters) {
        return new TargetPlatformFilterEvaluator(filters, logger);
    }

    private TargetPlatformFilterEvaluator newEvaluator(TargetPlatformFilter filter) {
        return new TargetPlatformFilterEvaluator(Collections.singletonList(filter), logger);
    }

    @Test
    public void testNoFilters() {
        List<TargetPlatformFilter> filters = Collections.<TargetPlatformFilter> emptyList();
        subject = newEvaluator(filters);

        subject.filterUnits(workUnits);

        assertThat(removedUnits(), hasSize(0));
    }

    @Test
    public void testRemoveAllOfBundleId() throws Exception {
        TargetPlatformFilter removeAllFilter = removeAllFilter(ALL_MULTIVERSION_BUNDLES);
        subject = newEvaluator(removeAllFilter);

        subject.filterUnits(workUnits);

        assertThat(removedUnits(), hasItem("trf.bundle.multiversion_1.0.0"));
        assertThat(removedUnits(), hasItem("trf.bundle.multiversion_2.0.0"));
        assertThat(removedUnits(), hasSize(2));
    }

    @Test
    public void testRemoveAllOfUnitId() throws Exception {
        CapabilityPattern unitIdPattern = patternWithVersion(CapabilityType.P2_INSTALLABLE_UNIT, "main.product.id",
                null);
        subject = newEvaluator(removeAllFilter(unitIdPattern));

        subject.filterUnits(workUnits);

        assertThat(removedUnits(), hasItem("main.product.id_0.0.1.201112271438"));
        assertThat(removedUnits(), hasSize(1));
    }

    @Test
    public void testUnitAndBundleIdDistinction() throws Exception {
        // does not match because main.product.id is not a bundle
        CapabilityPattern bundleIdPattern = patternWithVersion(CapabilityType.OSGI_BUNDLE, "main.product.id", null);
        subject = newEvaluator(removeAllFilter(bundleIdPattern));

        subject.filterUnits(workUnits);

        assertThat(removedUnits(), hasSize(0));
    }

    @Test
    public void testRestrictToExactVersion() throws Exception {
        TargetPlatformFilter versionFilter = restrictionFilter(ALL_MULTIVERSION_BUNDLES,
                patternWithVersion(null, null, "1.0"));
        subject = newEvaluator(versionFilter);

        subject.filterUnits(workUnits);

        assertThat(removedUnits(), hasItem("trf.bundle.multiversion_2.0.0"));
        assertThat(removedUnits(), hasSize(1));
    }

    @Test
    public void testRestrictToVersionRange() throws Exception {
        TargetPlatformFilter versionRangeFilter = restrictionFilter(ALL_MULTIVERSION_BUNDLES,
                patternWithVersionRange(null, null, "[1.0.0,2)"));
        subject = newEvaluator(versionRangeFilter);

        subject.filterUnits(workUnits);

        assertThat(removedUnits(), hasItem("trf.bundle.multiversion_2.0.0"));
        assertThat(removedUnits(), hasSize(1));
    }

    @Test
    public void testRestrictPackageProvider() throws Exception {
        TargetPlatformFilter providerFilter = restrictionFilter(
                patternWithoutVersion(CapabilityType.JAVA_PACKAGE, "javax.persistence"),
                patternWithoutVersion(CapabilityType.OSGI_BUNDLE, "javax.persistence"));
        subject = newEvaluator(providerFilter);

        subject.filterUnits(workUnits);

        assertThat(removedUnits(), hasItem("com.springsource.javax.persistence_1.0.0"));
        assertThat(removedUnits(), hasSize(1));
    }

    @Test
    public void testRestrictPackageVersionInShortNotation() throws Exception {
        TargetPlatformFilter packageVersionFilter = restrictionFilter(
                patternWithoutVersion(CapabilityType.JAVA_PACKAGE, "javax.persistence"),
                patternWithVersion(null, null, "1.0.0")); // inherit attributes from scope pattern
        subject = newEvaluator(packageVersionFilter);

        subject.filterUnits(workUnits);

        assertThat(removedUnits(), hasItem("javax.persistence_2.0.3.v201010191057")); // provides *a* package in version 1.0.0, but not the package javax.persistence
        assertThat(removedUnits(), hasSize(1));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testWarningIfRestrictionRemovesAll() throws Exception {
        TargetPlatformFilter versionFilter = restrictionFilter(ALL_MULTIVERSION_BUNDLES,
                patternWithVersion(null, null, "3.0.0"));
        subject = newEvaluator(versionFilter);

        subject.filterUnits(workUnits);

        // 3.0.0 doesn't exist, so all applicable units shall be removed...
        assertThat(removedUnits(), hasItem("trf.bundle.multiversion_1.0.0"));
        assertThat(removedUnits(), hasItem("trf.bundle.multiversion_2.0.0"));
        assertThat(removedUnits(), hasSize(2));

        // ... but this yields a warning
        assertThat(logger.warnings,
                hasItem(allOf(containsString("Removed all units"), containsString("trf.bundle.multiversion"))));
    }

    @Test(expected = TargetPlatformFilterSyntaxException.class)
    public void testNonParsableVersion() throws Exception {
        TargetPlatformFilter invalidFilter = restrictionFilter(ALL_MULTIVERSION_BUNDLES,
                patternWithVersion(null, null, "1.a"));
        subject = newEvaluator(invalidFilter);

        subject.filterUnits(workUnits);
    }

    @Test(expected = TargetPlatformFilterSyntaxException.class)
    public void testNonParsableVersionRange() throws Exception {
        TargetPlatformFilter invalidFilter = restrictionFilter(ALL_MULTIVERSION_BUNDLES,
                patternWithVersionRange(null, null, "[1.0.0,")); // "[1.0.0," is invalid; "1.0.0" is the range from 1 to infinity
        subject = newEvaluator(invalidFilter);

        subject.filterUnits(workUnits);
    }

    private Collection<String> removedUnits() {
        HashSet<String> result = new HashSet<String>();
        for (IInstallableUnit unit : baselineUnits) {
            if (!workUnits.contains(unit)) {
                result.add(unit.getId() + "_" + unit.getVersion());
            }
        }
        return result;
    }

    static Matcher<Collection<?>> hasSize(final int expectedSize) {
        return new TypeSafeMatcher<Collection<?>>() {
            @Override
            public boolean matchesSafely(Collection<?> collection) {
                return expectedSize == collection.size();
            }

            public void describeTo(Description description) {
                description.appendText("a collection of size ").appendValue(expectedSize);
            }
        };
    }

}
