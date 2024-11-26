package org.example.flowday.domain.post.post.service;

import lombok.RequiredArgsConstructor;
import org.example.flowday.domain.course.course.entity.Course;
import org.example.flowday.domain.course.course.exception.CourseException;
import org.example.flowday.domain.course.course.repository.CourseRepository;
import org.example.flowday.domain.course.spot.dto.SpotResDTO;
import org.example.flowday.domain.course.spot.entity.Spot;
import org.example.flowday.domain.member.entity.Member;
import org.example.flowday.domain.member.exception.MemberException;
import org.example.flowday.domain.member.repository.MemberRepository;
import org.example.flowday.domain.post.post.dto.GenFileResponseDTO;
import org.example.flowday.domain.post.post.dto.PostRequestDTO;
import org.example.flowday.domain.post.post.dto.PostResponseDTO;
import org.example.flowday.domain.post.post.entity.Post;
import org.example.flowday.domain.post.post.mapper.GenFileMapper;
import org.example.flowday.domain.post.post.mapper.PostMapper;
import org.example.flowday.domain.post.post.repository.PostRepository;
import org.example.flowday.global.config.AppConfig;
import org.example.flowday.global.fileupload.entity.GenFile;
import org.example.flowday.global.fileupload.repository.GenFileRepository;
import org.example.flowday.global.fileupload.service.GenFileService;
import org.example.flowday.global.security.util.SecurityUser;
import org.example.flowday.standard.util.Util;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final PostMapper postMapper;
    private final MemberRepository memberRepository;
    private final CourseRepository courseRepository;
    private final GenFileService  genFileService;

    @Transactional
    public PostResponseDTO createPost(PostRequestDTO postRequestDTO, Long userId) {
        Member writer = memberRepository.findById(userId)
                .orElseThrow(MemberException.MEMBER_NOT_FOUND::getMemberTaskException);

        Course course = null;
        List<SpotResDTO> spotResDTOs = null;

        if (postRequestDTO.getCourseId() != null) {
            course = courseRepository.findById(postRequestDTO.getCourseId())
                    .orElseThrow(CourseException.NOT_FOUND::get);
            List<Spot> spots = course.getSpots();
            spotResDTOs = spots.stream()
                    .map(SpotResDTO::new)
                    .collect(Collectors.toList());
        }

        // 게시글 생성 및 저장
        Post post = postMapper.toEntity(postRequestDTO, writer, course);
        Post savedPost = postRepository.save(post);

        // 이미지 저장 로직 추가
        List<MultipartFile> images = postRequestDTO.getImages();
        if (images != null && !images.isEmpty()) {
            genFileService.saveFiles(savedPost, images);
        }

        // 이미지 정보를 포함하여 응답 DTO 생성
        List<GenFile> genFiles = genFileService.getFilesByPost(savedPost);
        List<GenFileResponseDTO> imageDTOs = genFiles.stream()
                .map(GenFileMapper::toResponseDTO)
                .collect(Collectors.toList());

        return postMapper.toResponseDTO(savedPost, spotResDTOs, imageDTOs);
    }

    // 게시글 디테일 - ID
    public PostResponseDTO getPostById(Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 게시글이 존재하지 않습니다"));

        Course course = post.getCourse();
        List<SpotResDTO> spotResDTOs = null;
        if (course != null) {
            List<Spot> spots = course.getSpots();
            spotResDTOs = spots.stream()
                    .map(SpotResDTO::new)
                    .collect(Collectors.toList());
        }

        // 이미지 정보 가져오기
        List<GenFile> genFiles = genFileService.getFilesByPost(post);
        List<GenFileResponseDTO> imageDTOs = genFiles.stream()
                .map(GenFileMapper::toResponseDTO)
                .collect(Collectors.toList());

        return postMapper.toResponseDTO(post, spotResDTOs, imageDTOs);
    }

    // 모든 게시글 조회 최신순
    public Page<PostResponseDTO> getAllPosts(Pageable pageable) {
        Page<Post> posts = postRepository.searchLatestPost(pageable);

        return posts.map(post -> postMapper.toResponseDTO(post , null, null));

    }

    // 게시글 수정
//    @Transactional
//    public PostResponseDTO updatePost(Long id, PostRequestDTO updatedPostDTO) {
//        Post post = postRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));
//        post.setTitle(updatedPostDTO.getTitle());
//        post.setContents(updatedPostDTO.getContents());
//        post.setCity(updatedPostDTO.getCity());
//        post.setCourseId(updatedPostDTO.getCourseId());
//        return postMapper.toResponseDTO(post);
//    }

    // 게시글 삭제
    @Transactional
    public void deletePost(Long id) {
        postRepository.deleteById(id);
    }


    public void addGenFileByUrl(Post post, String typeCode, String type2Code, int fileNo, String url) {
        genFileService.addGenFileByUrl("post", post.getId(), typeCode, type2Code, fileNo, url);
    }

    public Post getPosteById(Long id) {
        return postRepository.findById(id).orElse(null);
    }

    public Map<String,Object> getForPrintArticleById(Long id) {
        Post post = getPosteById(id);
        Map<String, GenFile> genFileMap = genFileService.getRelGenFileMap(post);


        post.getExtra().put("genFileMap", genFileMap);

        return post.getExtra();
    }

}