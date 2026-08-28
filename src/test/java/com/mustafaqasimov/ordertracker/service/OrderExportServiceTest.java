package com.mustafaqasimov.ordertracker.service;

import com.mustafaqasimov.ordertracker.entity.Order;
import com.mustafaqasimov.ordertracker.entity.User;
import com.mustafaqasimov.ordertracker.enums.OrderStatus;
import com.mustafaqasimov.ordertracker.repository.OrderRepository;
import com.mustafaqasimov.ordertracker.support.TestFixtures;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderExportService")
class OrderExportServiceTest {

    @Mock OrderRepository orderRepository;
    @InjectMocks OrderExportService exportService;

    private Order order;

    @BeforeEach
    void setUp() {
        User owner = TestFixtures.user(7L, "user@test.local");
        order = TestFixtures.order(1L, owner, OrderStatus.PAID);
    }

    @Test
    @DisplayName("CSV starts with the header row and holds one line per order")
    void csvLayout() {
        when(orderRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order)));

        String csv = new String(exportService.exportToCsv(null, PageRequest.of(0, 20)),
                StandardCharsets.UTF_8);

        String[] lines = csv.split("\n");
        assertThat(lines).hasSize(2);
        assertThat(lines[0]).isEqualTo("Order Number,Status,Total,Currency,Customer Email,Created At");
        assertThat(lines[1])
                .startsWith("ORD-20260101-ABCD1234,PAID,59.98,USD,user@test.local,")
                .contains("2026-01-01T10:00");
    }

    @Test
    @DisplayName("an empty result still produces the header")
    void csvWithNoOrders() {
        when(orderRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        String csv = new String(exportService.exportToCsv(null, PageRequest.of(0, 20)),
                StandardCharsets.UTF_8);

        assertThat(csv).isEqualTo("Order Number,Status,Total,Currency,Customer Email,Created At\n");
    }

    @Test
    @DisplayName("a status filter switches the query to findByStatus")
    void statusFilterSelectsTheFilteredQuery() {
        Pageable pageable = PageRequest.of(0, 20);
        when(orderRepository.findByStatus(OrderStatus.PAID, pageable))
                .thenReturn(new PageImpl<>(List.of(order)));

        exportService.exportToCsv(OrderStatus.PAID, pageable);

        verify(orderRepository).findByStatus(OrderStatus.PAID, pageable);
        verify(orderRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("an oversized page request is capped at 5000 rows")
    void capsPageSize() {
        when(orderRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        exportService.exportToCsv(null, PageRequest.of(2, 100_000, Sort.by("createdAt")));

        ArgumentCaptor<Pageable> used = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findAll(used.capture());
        assertThat(used.getValue().getPageSize()).isEqualTo(5000);
        assertThat(used.getValue().getPageNumber()).isEqualTo(2);
        assertThat(used.getValue().getSort()).isEqualTo(Sort.by("createdAt"));
    }

    @Test
    @DisplayName("a page size within the limit is passed through untouched")
    void keepsReasonablePageSize() {
        Pageable pageable = PageRequest.of(0, 50);
        when(orderRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of()));

        exportService.exportToCsv(null, pageable);

        verify(orderRepository).findAll(pageable);
    }

    @Test
    @DisplayName("the Excel export is a readable workbook with a header and the data rows")
    void excelLayout() throws IOException {
        when(orderRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order)));

        byte[] bytes = exportService.exportToExcel(null, PageRequest.of(0, 20));

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheet("Orders");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getLastRowNum()).isEqualTo(1);

            Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Order Number");
            assertThat(header.getCell(5).getStringCellValue()).isEqualTo("Created At");

            Row data = sheet.getRow(1);
            assertThat(data.getCell(0).getStringCellValue()).isEqualTo("ORD-20260101-ABCD1234");
            assertThat(data.getCell(1).getStringCellValue()).isEqualTo("PAID");
            assertThat(data.getCell(2).getStringCellValue()).isEqualTo("59.98");
            assertThat(data.getCell(3).getStringCellValue()).isEqualTo("USD");
            assertThat(data.getCell(4).getStringCellValue()).isEqualTo("user@test.local");
        }
    }

    @Test
    @DisplayName("an empty Excel export still carries the header row")
    void excelWithNoOrders() throws IOException {
        when(orderRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        byte[] bytes = exportService.exportToExcel(null, PageRequest.of(0, 20));

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheet("Orders");
            assertThat(sheet.getLastRowNum()).isZero();
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Order Number");
        }
    }
}
