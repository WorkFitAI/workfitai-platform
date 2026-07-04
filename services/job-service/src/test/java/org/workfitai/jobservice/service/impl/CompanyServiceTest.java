package org.workfitai.jobservice.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.workfitai.jobservice.model.Company;
import org.workfitai.jobservice.model.dto.request.Company.ReqCreateCompanyDTO;
import org.workfitai.jobservice.model.dto.request.Company.ReqUpdateCompanyDTO;
import org.workfitai.jobservice.model.dto.response.Company.ResCompanyDTO;
import org.workfitai.jobservice.model.dto.response.Company.ResUpdateCompanyDTO;
import org.workfitai.jobservice.model.mapper.CompanyMapper;
import org.workfitai.jobservice.repository.CompanyRepository;
import org.workfitai.jobservice.service.CloudinaryService;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {

    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private CompanyMapper companyMapper;
    @Mock
    private CloudinaryService cloudinaryService;
    @InjectMocks
    private CompanyService companyService;

    private Company company() {
        return Company.builder().companyNo("C-A").name("FPT").build();
    }

    @Test
    void getById_returnsMappedDTO_whenFound() {
        Company company = company();
        when(companyRepository.findById("C-A")).thenReturn(Optional.of(company));
        ResCompanyDTO dto = ResCompanyDTO.builder().build();
        when(companyMapper.toResDTO(company)).thenReturn(dto);

        assertThat(companyService.getById("C-A")).isSameAs(dto);
    }

    @Test
    void getById_throws_whenNotFound() {
        when(companyRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> companyService.getById("missing"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Company not found");
    }

    @Test
    void create_savesEntityAndReturnsDTO() {
        ReqCreateCompanyDTO dto = ReqCreateCompanyDTO.builder().companyNo("C-A").name("FPT").build();
        Company entity = company();
        ResCompanyDTO resDto = ResCompanyDTO.builder().build();
        when(companyMapper.toEntity(dto)).thenReturn(entity);
        when(companyMapper.toResDTO(entity)).thenReturn(resDto);

        ResCompanyDTO result = companyService.create(dto);

        verify(companyRepository).save(entity);
        assertThat(result).isSameAs(resDto);
    }

    @Test
    void update_uploadsLogo_whenLogoFileProvided() throws IOException {
        Company company = company();
        ReqUpdateCompanyDTO dto = ReqUpdateCompanyDTO.builder()
                .companyNo("C-A")
                .logoFile(new MockMultipartFile("logo", "logo.png", "image/png", new byte[] { 1, 2, 3 }))
                .build();
        when(companyRepository.findById("C-A")).thenReturn(Optional.of(company));
        when(cloudinaryService.uploadFile(any(), eq("C-A"))).thenReturn("https://cdn/logo.png");
        ResUpdateCompanyDTO resDto = new ResUpdateCompanyDTO();
        when(companyMapper.toResUpdateDTO(company)).thenReturn(resDto);

        ResUpdateCompanyDTO result = companyService.update(dto);

        assertThat(company.getLogoUrl()).isEqualTo("https://cdn/logo.png");
        verify(companyMapper).updateEntityFromDTO(dto, company);
        verify(companyRepository).save(company);
        assertThat(result).isSameAs(resDto);
    }

    @Test
    void update_skipsLogoUpload_whenNoLogoFile() {
        Company company = company();
        ReqUpdateCompanyDTO dto = ReqUpdateCompanyDTO.builder().companyNo("C-A").build();
        when(companyRepository.findById("C-A")).thenReturn(Optional.of(company));
        when(companyMapper.toResUpdateDTO(company)).thenReturn(new ResUpdateCompanyDTO());

        companyService.update(dto);

        verify(companyRepository).save(company);
        assertThat(company.getLogoUrl()).isNull();
    }

    @Test
    void update_wrapsIOException_whenUploadFails() throws IOException {
        Company company = company();
        ReqUpdateCompanyDTO dto = ReqUpdateCompanyDTO.builder()
                .companyNo("C-A")
                .logoFile(new MockMultipartFile("logo", "logo.png", "image/png", new byte[] { 1 }))
                .build();
        when(companyRepository.findById("C-A")).thenReturn(Optional.of(company));
        when(cloudinaryService.uploadFile(any(), eq("C-A"))).thenThrow(new IOException("upload failed"));

        assertThatThrownBy(() -> companyService.update(dto))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to upload logo")
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void update_throws_whenCompanyNotFound() {
        when(companyRepository.findById("missing")).thenReturn(Optional.empty());
        ReqUpdateCompanyDTO dto = ReqUpdateCompanyDTO.builder().companyNo("missing").build();

        assertThatThrownBy(() -> companyService.update(dto))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Company not found");
    }

    @Test
    void delete_removesCompany_whenExists() {
        when(companyRepository.existsById("C-A")).thenReturn(true);

        companyService.delete("C-A");

        verify(companyRepository).deleteById("C-A");
    }

    @Test
    void delete_throws_whenNotExists() {
        when(companyRepository.existsById("missing")).thenReturn(false);

        assertThatThrownBy(() -> companyService.delete("missing"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Company not found");
        verify(companyRepository, never()).deleteById(any());
    }
}
