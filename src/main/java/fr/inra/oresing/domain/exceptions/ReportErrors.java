package fr.inra.oresing.domain.exceptions;


import fr.inra.oresing.domain.Mapper;
import fr.inra.oresing.domain.data.deposit.validation.CsvRowValidationCheckResult;

import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

public class ReportErrors extends ConcurrentLinkedQueue<CsvRowValidationCheckResult> {

    private static final int MAX_ERRORS_SIZE = 15;
    private static final long MAX_ERRORS_BYTE = 1000000;
    final Mapper jsonRowMapper;
    private final AtomicLong length = new AtomicLong();

    public ReportErrors(final Mapper jsonRowMapper) {
        this.jsonRowMapper = jsonRowMapper;
    }

    @Override
    public boolean add(final CsvRowValidationCheckResult csvRowValidationCheckResult) {
        return test(csvRowValidationCheckResult);
    }

    private boolean test(final CsvRowValidationCheckResult csvRowValidationCheckResult) {
        final String str = jsonRowMapper.toJson(csvRowValidationCheckResult);
        final long newLength = length.addAndGet(str.codePointCount(0, str.length()));
        return isOverload(newLength) && super.add(csvRowValidationCheckResult);
    }

    @Override
    public boolean addAll(final Collection<? extends CsvRowValidationCheckResult> c) {
        return c.stream()
                .allMatch(this::add);
    }

    public boolean canRegisterErrors() {
        return size() < MAX_ERRORS_SIZE && isOverload(length.get());
    }

    private boolean isOverload(long length) {
        return length <= MAX_ERRORS_BYTE;
    }
}