/*
 * Waltz - Enterprise Architecture
 * Copyright (C) 2016, 2017, 2018, 2019 Waltz open source project
 * See README.md for more information
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific
 *
 */

package com.khartec.waltz.data.person.search;

import com.khartec.waltz.data.person.PersonDao;
import com.khartec.waltz.model.EntityLifecycleStatus;
import com.khartec.waltz.model.entity_search.EntitySearchOptions;
import com.khartec.waltz.model.person.Person;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.khartec.waltz.common.SetUtilities.orderedUnion;
import static com.khartec.waltz.data.SearchUtilities.mkTerms;
import static com.khartec.waltz.schema.tables.Person.PERSON;

@Repository
public class PersonSearchDao {

    private final DSLContext dsl;


    @Autowired
    public PersonSearchDao(DSLContext dsl) {
        this.dsl = dsl;
    }


    public List<Person> search(EntitySearchOptions options) {
        List<String> terms = mkTerms(options.searchQuery());
        if (terms.isEmpty()) {
            return Collections.emptyList();
        }

        Condition displayNameCondition = terms.stream()
                .map(PERSON.DISPLAY_NAME::containsIgnoreCase)
                .collect(Collectors.reducing(
                        DSL.trueCondition(),
                        (acc, frag) -> acc.and(frag)));

        List<Person> peopleViaEmail = executeWithCondition(options, PERSON.EMAIL.startsWithIgnoreCase(options.searchQuery()));
        List<Person> peopleViaName = executeWithCondition(options, displayNameCondition);

        return new ArrayList<>(orderedUnion(peopleViaEmail, peopleViaName));
    }

    private List<Person> executeWithCondition(EntitySearchOptions options, Condition condition) {
        boolean showRemoved = options
                .entityLifecycleStatuses()
                .contains(EntityLifecycleStatus.REMOVED);

        Condition maybeFilterRemoved = showRemoved
                ? DSL.trueCondition()  // match anything
                : PERSON.IS_REMOVED.isFalse();

        return dsl
                .select(PERSON.fields())
                .from(PERSON)
                .where(condition)
                .and(maybeFilterRemoved)
                .limit(options.limit())
                .fetch(PersonDao.personMapper);
    }
}
