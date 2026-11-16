package com.employeehub.config;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

/**
 * Stores LocalDate as midnight UTC so a joining date never shifts when the JVM
 * time zone differs between machines (Spring Data's default uses the system zone).
 */
@Configuration
public class MongoConfig {

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(List.of(
                LocalDateToDateConverter.INSTANCE, DateToLocalDateConverter.INSTANCE));
    }

    @WritingConverter
    enum LocalDateToDateConverter implements Converter<LocalDate, Date> {
        INSTANCE;

        @Override
        public Date convert(LocalDate source) {
            return Date.from(source.atStartOfDay(ZoneOffset.UTC).toInstant());
        }
    }

    @ReadingConverter
    enum DateToLocalDateConverter implements Converter<Date, LocalDate> {
        INSTANCE;

        @Override
        public LocalDate convert(Date source) {
            return source.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        }
    }
}
