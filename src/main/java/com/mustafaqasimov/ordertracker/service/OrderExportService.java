package com.mustafaqasimov.ordertracker.service;

import com.mustafaqasimov.ordertracker.entity.Order;
import com.mustafaqasimov.ordertracker.enums.OrderStatus;
import com.mustafaqasimov.ordertracker.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderExportService {

    private static final int MAX_EXPORT_SIZE = 5000;

    private final OrderRepository orderRepository;

    public byte[] exportToCsv(OrderStatus statusFilter, Pageable pageable) {
        List<Order> orders = fetch(statusFilter, pageable);
        StringBuilder sb = new StringBuilder();
        sb.append("Order Number,Status,Total,Currency,Customer Email,Created At\n");
        for (Order o : orders) {
            sb.append(o.getOrderNumber()).append(',')
                    .append(o.getStatus()).append(',')
                    .append(o.getTotalAmount()).append(',')
                    .append(o.getCurrency()).append(',')
                    .append(o.getCustomerEmail()).append(',')
                    .append(o.getCreatedAt()).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] exportToExcel(OrderStatus statusFilter, Pageable pageable) throws IOException {
        List<Order> orders = fetch(statusFilter, pageable);

        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {

            Sheet sheet = wb.createSheet("Orders");

            // --- Header stili ---
            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            String[] cols = {"Order Number", "Status", "Total", "Currency", "Customer Email", "Created At"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < cols.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(headerStyle);
            }

            // --- Data stili (border) ---
            CellStyle dataStyle = wb.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);

            int rowIdx = 1;
            for (Order o : orders) {
                Row row = sheet.createRow(rowIdx++);
                Object[] values = {
                        o.getOrderNumber(), o.getStatus().toString(),
                        o.getTotalAmount().toPlainString(), o.getCurrency(),
                        o.getCustomerEmail(), o.getCreatedAt().toString()
                };
                for (int i = 0; i < values.length; i++) {
                    Cell cell = row.createCell(i);
                    cell.setCellValue(values[i].toString());
                    cell.setCellStyle(dataStyle);
                }
            }

            for (int i = 0; i < cols.length; i++) {
                sheet.setColumnWidth(i, 5000);
            }

            sheet.createFreezePane(0, 1);

            wb.write(bos);
            return bos.toByteArray();
        }
    }

    private List<Order> fetch(OrderStatus statusFilter, Pageable pageable) {
        pageable = capSize(pageable);
        return statusFilter == null
                ? orderRepository.findAll(pageable).getContent()
                : orderRepository.findByStatus(statusFilter, pageable).getContent();
    }

    private Pageable capSize(Pageable pageable) {
        if (pageable.getPageSize() > MAX_EXPORT_SIZE) {
            return PageRequest.of(pageable.getPageNumber(), MAX_EXPORT_SIZE, pageable.getSort());
        }
        return pageable;
    }
}
