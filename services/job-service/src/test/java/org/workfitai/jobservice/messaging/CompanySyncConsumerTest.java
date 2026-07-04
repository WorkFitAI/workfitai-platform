package org.workfitai.jobservice.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.workfitai.jobservice.model.Company;
import org.workfitai.jobservice.model.dto.kafka.CompanySyncEvent;
import org.workfitai.jobservice.repository.CompanyRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanySyncConsumerTest {

    @Mock
    private CompanyRepository companyRepository;
    @InjectMocks
    private CompanySyncConsumer companySyncConsumer;

    private CompanySyncEvent.CompanyData companyData() {
        return CompanySyncEvent.CompanyData.builder()
                .companyNo("C-A").name("FPT Software").address("Hanoi").size("1000+").build();
    }

    @Test
    void handleCompanySync_createsNewCompany_whenNotExisting() {
        when(companyRepository.findById("C-A")).thenReturn(Optional.empty());
        when(companyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        CompanySyncEvent event = CompanySyncEvent.builder().eventType("COMPANY_CREATED").company(companyData()).build();

        companySyncConsumer.handleCompanySync(event);

        ArgumentCaptor<Company> captor = ArgumentCaptor.forClass(Company.class);
        verify(companyRepository).save(captor.capture());
        assertThat(captor.getValue().getCompanyNo()).isEqualTo("C-A");
        assertThat(captor.getValue().getName()).isEqualTo("FPT Software");
    }

    @Test
    void handleCompanySync_updatesExistingCompany() {
        Company existing = Company.builder().companyNo("C-A").name("Old Name").build();
        when(companyRepository.findById("C-A")).thenReturn(Optional.of(existing));
        when(companyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        CompanySyncEvent event = CompanySyncEvent.builder().eventType("COMPANY_UPDATED").company(companyData()).build();

        companySyncConsumer.handleCompanySync(event);

        verify(companyRepository).save(existing);
        assertThat(existing.getName()).isEqualTo("FPT Software");
    }

    @Test
    void handleCompanySync_doesNothing_whenEventIsNull() {
        companySyncConsumer.handleCompanySync(null);

        verify(companyRepository, never()).save(any());
    }

    @Test
    void handleCompanySync_doesNothing_whenCompanyDataIsNull() {
        CompanySyncEvent event = CompanySyncEvent.builder().eventType("COMPANY_CREATED").company(null).build();

        companySyncConsumer.handleCompanySync(event);

        verify(companyRepository, never()).save(any());
    }

    @Test
    void handleCompanySync_wrapsException_whenRepositoryFails() {
        when(companyRepository.findById("C-A")).thenThrow(new RuntimeException("db down"));
        CompanySyncEvent event = CompanySyncEvent.builder().eventType("COMPANY_CREATED").company(companyData()).build();

        assertThatThrownBy(() -> companySyncConsumer.handleCompanySync(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to process company sync event");
    }
}
