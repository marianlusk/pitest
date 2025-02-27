/*
 * Copyright 2010 Henry Coles
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */
package org.pitest.mutationtest.engine.gregor;

import org.junit.Test;
import org.pitest.mutationtest.engine.MutationDetails;
import org.pitest.mutationtest.engine.gregor.config.Mutator;
import org.pitest.mutationtest.engine.gregor.mutators.IncrementsMutator;
import org.pitest.mutationtest.engine.gregor.mutators.InvertNegsMutator;
import org.pitest.mutationtest.engine.gregor.mutators.MathMutator;
import org.pitest.mutationtest.engine.gregor.mutators.NegateConditionalsMutator;
import org.pitest.mutationtest.engine.gregor.mutators.ReturnValsMutator;
import org.pitest.util.ResourceFolderByteArraySource;
import org.pitest.verifier.mutants.MutatorVerifierStart;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestGregorMutater {

    @Test
    public void shouldFindMutationsFromAllSuppliedMutators() {

        MutatorVerifierStart v = MutatorVerifierStart.forMutator(MathMutator.MATH,
                ReturnValsMutator.RETURN_VALS,
                InvertNegsMutator.INVERT_NEGS,
                IncrementsMutator.INCREMENTS);

        List<MutationDetails> actualDetails = v.forClass(HasMultipleMutations.class)
                .findMutations();

        assertTrue(actualDetails.stream()
                .anyMatch(descriptionContaining("Replaced Shift Left with Shift Right")));
        assertTrue(actualDetails.stream()
                .anyMatch(descriptionContaining("replaced return of integer")));
        assertTrue(actualDetails.stream()
                .anyMatch(descriptionContaining("Changed increment")));
        assertTrue(actualDetails.stream()
                .anyMatch(descriptionContaining("removed negation")));
    }

    @Test
    public void shouldFindNoMutationsWhenNoMutationOperatorsSupplied() {
        class VeryMutable {
            @SuppressWarnings("unused")
            public int f(final int i) {
                switch (i) {
                    case 0:
                        return 1;
                }
                return 0;
            }
        }
        MutatorVerifierStart.forMutator()
                .forClass(VeryMutable.class)
                .noMutantsCreated();
    }

    @Test
    public void shouldNotMutateCodeGeneratedByCompilerToImplementEnums() {
        MutatorVerifierStart.forMutator(Mutator.all())
                .forClass(AnEnum.class)
                .noMutantsCreated();
    }

    @Test
    public void shouldMutateCustomConstructorsAddedToEnums() {
        Collection<MutationDetails> actualDetails = MutatorVerifierStart.forMutator(Mutator.all())
                .forClass(EnumWithCustomConstructor.class)
                .findMutations();

        assertThat(actualDetails).isNotEmpty();
    }

    @Test
    public void shouldNotMutateAssertStatements() {
        MutatorVerifierStart.forMutator(NegateConditionalsMutator.NEGATE_CONDITIONALS)
                .forClass(HasAssertStatement.class)
                .noMutantsCreated();
    }

    @Test
    public void shouldMutateOtherStatementsWhenAssertIsPresent() {
        MutatorVerifierStart.forMutator(NegateConditionalsMutator.NEGATE_CONDITIONALS)
                .forClass(HasAssertStatementAndOtherStatements.class)
                .createsNMutants(1);
    }

    @Test
    public void shouldNotMutateGroovyClasses() {
        MutatorVerifierStart.forMutator(Mutator.all())
                .withByteArraySource(new ResourceFolderByteArraySource())
                .forClass("groovy/SomeGroovyCode")
                .noMutantsCreated();
    }

    @Test
    public void shouldNotMutateGroovyClosures() {
        MutatorVerifierStart.forMutator(Mutator.all())
                .withByteArraySource(new ResourceFolderByteArraySource())
                .forClass("groovy/SomeGroovyCode$_mapToString_closure2")
                .noMutantsCreated();
    }

    @Test
    public void shouldRecordMutationsAsInSameBlockWhenForAStraightThroughMethod() {
        List<MutationDetails> actualDetails = MutatorVerifierStart.forMutator(IncrementsMutator.INCREMENTS)
                .forClass(OneStraightThroughMethod.class)
                .findMutations();

        assertEquals(2, actualDetails.size());
        final int firstMutationBlock = actualDetails.get(0).getBlock();
        assertEquals(firstMutationBlock, actualDetails.get(1).getBlock());
    }

    @Test
    public void shouldRecordMutationsAsInDifferentBlocksWhenInDifferentBranchesOfIfStatement() {
        List<MutationDetails> actualDetails = MutatorVerifierStart.forMutator(IncrementsMutator.INCREMENTS)
                .forClass(SimpleBranch.class)
                .findMutations();

        assertTwoMutationsInDifferentBlocks(actualDetails);
    }

    @Test
    public void shouldRecordMutationsAsInDifferentBlocksWhenInDifferentBranchesOfSwitchStatement() {
        List<MutationDetails> actualDetails = MutatorVerifierStart.forMutator(IncrementsMutator.INCREMENTS)
                .forClass(SwitchStatement.class)
                .findMutations();

        assertEquals(3, actualDetails.size());
        final int firstMutationBlock = actualDetails.get(0).getBlock();
        assertEquals(firstMutationBlock + 1, actualDetails.get(1).getBlock());
        assertEquals(firstMutationBlock + 2, actualDetails.get(2).getBlock());
    }

    @Test
    public void shouldNotRecordMutationsAsInSameBlockWhenSwitchStatementFallsThrough() {
        List<MutationDetails> actualDetails = MutatorVerifierStart.forMutator(IncrementsMutator.INCREMENTS)
                .forClass(FallThroughSwitch.class)
                .findMutations();

        assertEquals(2, actualDetails.size());
        final int firstMutationBlock = actualDetails.get(0).getBlock();
        assertEquals(firstMutationBlock + 1, actualDetails.get(1).getBlock());
    }

    @Test
    public void shouldRecordMutationsAsInDifferentBlocksWhenInExceptionHandler() {
        List<MutationDetails> actualDetails = MutatorVerifierStart.forMutator(IncrementsMutator.INCREMENTS)
                .forClass(HasExceptionBlock.class)
                .findMutations();

        assertTwoMutationsInDifferentBlocks(actualDetails);
    }

    @Test
    public void shouldScopeMutationIndexesByInstructionCounter() {
        List<MutationDetails> actualDetails = MutatorVerifierStart.forMutator(ReturnValsMutator.RETURN_VALS)
                .forClass(HasTwoMutableMethods.class)
                .findMutations();

        assertEquals(2, actualDetails.size());
        assertEquals(4, actualDetails.get(0).getId().getFirstIndex());
        assertEquals(15, actualDetails.get(1).getId().getFirstIndex()); // differs
        // by
        // target?
    }

    @Test
    public void shouldNotMutateCompilerGeneratedConditionalsInStringSwitch() {
        MutatorVerifierStart.forMutator(Mutator.byName("REMOVE_CONDITIONALS"))
                .withByteArraySource(new ResourceFolderByteArraySource())
                .forClass("Java7SwitchOnString")
                .noMutantsCreated();

    }

    private void assertTwoMutationsInDifferentBlocks(
            final List<MutationDetails> actualDetails) {
        assertEquals(2, actualDetails.size());
        final int firstMutationBlock = actualDetails.get(0).getBlock();
        assertEquals(firstMutationBlock + 1, actualDetails.get(1).getBlock());
    }

    enum AnEnum {
        Foo, Bar;
    }

    enum EnumWithCustomConstructor {
        Foo, Bar;

        int i;

        EnumWithCustomConstructor() {
            this.i++;
        }

    }

    public static class HasMultipleMutations {
        public int mutable() {
            int j = 10;
            for (int i = 0; i != 10; i++) {
                j = j << 1;
            }

            return -j;
        }

    }

    public static class HasAssertStatement {
        public void foo(final int i) {
            assert ((i + 20) > 10);
        }
    }

    public static class HasAssertStatementAndOtherStatements {
        public int state;

        public void foo(final int i) {
            assert ((i + 20) > 10);
            if (i > 1) {
                this.state = 1;
            }
        }
    }

    public static class OneStraightThroughMethod {
        public void straightThrough(int i) {
            i++;
            i++;
        }
    }

    public static class SimpleBranch {
        public void straightThrough(int i, final boolean b) {
            if (b) {
                i++;
            } else {
                i++;
            }
        }
    }

    public static class SwitchStatement {
        public void a(int i, final int b) {
            switch (b) {
                case 0:
                    i++;
                    break;
                case 1:
                    i++;
                    break;
                default:
                    i++;
            }
        }

    }

    public static class FallThroughSwitch {
        public void a(int i, final int b) {
            switch (b) {
                case 0:
                    i++;
                case 1:
                    i++;
            }
        }
    }

    public static class HasExceptionBlock {
        public void foo(int i) {
            try {
                i++;
            } catch (final Exception ex) {
                i++;
            }
        }
    }

    public static class HasTwoMutableMethods {
        public int a() {
            return 1;
        }

        public int a(int i) {
            if (i > 2) {
                System.out.println(i);
            }
            return 1;
        }
    }

  Predicate<MutationDetails> descriptionContaining(final String value) {
    return a -> a.getDescription().contains(value);
  }
}
