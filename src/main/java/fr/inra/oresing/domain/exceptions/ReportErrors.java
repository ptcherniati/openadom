package fr.inra.oresing.domain.exceptions;


import fr.inra.oresing.domain.Mapper;
import fr.inra.oresing.domain.data.deposit.validation.CsvRowValidationCheckResult;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class ReportErrors extends LinkedList<CsvRowValidationCheckResult> {

    final Mapper jsonRowMapper;
    private long length;

    public ReportErrors(final List<CsvRowValidationCheckResult> list, final Mapper jsonRowMapper) {
        super(list);
        this.jsonRowMapper = jsonRowMapper;
    }

    public ReportErrors(final Mapper jsonRowMapper) {
        this.jsonRowMapper = jsonRowMapper;
    }

    @Override
    public boolean add(final CsvRowValidationCheckResult csvRowValidationCheckResult) {
        return test(csvRowValidationCheckResult);
    }

    private boolean test(final CsvRowValidationCheckResult csvRowValidationCheckResult) {
        final String str = jsonRowMapper.toJson(csvRowValidationCheckResult);
        length += str.codePointCount(0, str.length());
        return isOverload() && super.add(csvRowValidationCheckResult);
    }

    @Override
    public boolean addAll(final Collection<? extends CsvRowValidationCheckResult> c) {
        return c.stream()
                .allMatch(this::add);
    }

    public boolean canRegisterErrors() {
        return size() < 50 && isOverload();
    }

    private boolean isOverload() {
        return length <= 1000000;
    }
}
