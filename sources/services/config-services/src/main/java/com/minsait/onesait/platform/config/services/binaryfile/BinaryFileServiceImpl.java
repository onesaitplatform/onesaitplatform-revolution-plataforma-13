/**
 * Copyright Indra Soluciones Tecnologías de la Información, S.L.U.
 * 2013-2019 SPAIN
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.minsait.onesait.platform.config.services.binaryfile;

import java.util.ArrayList;
import java.util.List;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.minsait.onesait.platform.commons.exception.GenericOPException;
import com.minsait.onesait.platform.config.model.BinaryFile;
import com.minsait.onesait.platform.config.model.BinaryFileAccess;
import com.minsait.onesait.platform.config.model.BinaryFileAccess.Type;
import com.minsait.onesait.platform.config.model.Role;
import com.minsait.onesait.platform.config.model.User;
import com.minsait.onesait.platform.config.repository.BinaryFileAccessRepository;
import com.minsait.onesait.platform.config.repository.BinaryFileRepository;
import com.minsait.onesait.platform.config.services.user.UserService;

@Service
public class BinaryFileServiceImpl implements BinaryFileService {

	private static final String RT_EXCEP = "Run time exception";

	@Autowired
	private BinaryFileRepository binaryFileRepository;
	@Autowired
	private BinaryFileAccessRepository accessRepository;
	@Autowired
	private UserService userService;

	@Override
	public void createBinaryile(BinaryFile binaryFile) {
		binaryFileRepository.save(binaryFile);
	}

	@Override
	public void updateBinaryFile(String id, String metadata, String mime, String fileName) {
		final BinaryFile file = binaryFileRepository.findByFileId(id);
		if (!StringUtils.isEmpty(metadata))
			file.setMetadata(metadata);
		if (!StringUtils.isEmpty(mime))
			file.setMime(mime);
		if (!StringUtils.isEmpty(fileName))
			file.setFileName(fileName);
		binaryFileRepository.save(file);
	}

	@Override
	public boolean hasUserPermissionWrite(String id, User user) {
		return (user.getRole().getId().equals(Role.Type.ROLE_ADMINISTRATOR.name())
				|| binaryFileRepository.findByUserAndFileIdWrite(user, id) != null);
	}

	@Override
	public boolean hasUserPermissionRead(String id, User user) {
		if (user == null) {
			return (binaryFileRepository.findByFileId(id).isPublic());

		} else {
			return (user.getRole().getId().equals(Role.Type.ROLE_ADMINISTRATOR.name())
					|| binaryFileRepository.findByUserAndFileId(user, id) != null);
		}
	}

	@Override
	public void authorizeUser(String id, Type accessType, User user) {
		final BinaryFile file = binaryFileRepository.findByFileId(id);
		if (file != null) {
			BinaryFileAccess access = accessRepository.findByUserAndBinaryFile(user, file);
			if (access == null) {
				access = new BinaryFileAccess();
				access.setAccessType(accessType);
				access.setUser(user);
				access.setBinaryFile(file);
				file.getFileAccesses().add(access);
			} else {
				access.setAccessType(accessType);
				file.getFileAccesses().add(access);
			}
			binaryFileRepository.save(file);
			accessRepository.save(access);
		}
	}

	@Override
	@Transactional
	public void deleteFile(String fileId) {
		binaryFileRepository.deleteByFileId(fileId);
	}

	@Override
	public List<BinaryFile> getAllFiles(User user) {
		if (user.getRole().getId().equals(Role.Type.ROLE_ADMINISTRATOR.name()))
			return binaryFileRepository.findAll();
		return binaryFileRepository.findByUser(user);
	}

	@Override
	public BinaryFile getFile(String fileId) {
		return binaryFileRepository.findByFileId(fileId);
	}

	@Override
	public void changePublic(String fileId) {
		final BinaryFile file = binaryFileRepository.findByFileId(fileId);
		if (file != null) {
			file.setPublic(!file.isPublic());
			binaryFileRepository.save(file);
		}

	}

	@Override
	public boolean isUserOwner(String fileId, User user) {
		final BinaryFile file = binaryFileRepository.findByFileId(fileId);
		return (file.getOwner().getUserId().equalsIgnoreCase(user.getUserId())
				|| user.getRole().getId().equals(Role.Type.ROLE_ADMINISTRATOR.name()));
	}

	@Override
	public BinaryFileAccess createBinaryFileAccess(String fileId, String userId, String accessType, User user)
			throws GenericOPException {
		if (hasUserPermissionWrite(fileId, user)) {
			final BinaryFileAccess access = new BinaryFileAccess();
			access.setBinaryFile(getFile(fileId));
			access.setUser(userService.getUser(userId));
			access.setAccessType(Type.valueOf(accessType));
			return accessRepository.save(access);
		}
		throw new GenericOPException(RT_EXCEP);
	}

	@Override
	public List<BinaryFileAccess> getAuthorizations(String id, User user) {
		if (hasUserPermissionWrite(id, user)) {
			return accessRepository.findByBinaryFile(getFile(id));
		}
		return new ArrayList<>();
	}

	@Override
	@Transactional
	public void deleteBinaryFileAccess(String id, User user) throws GenericOPException {
		if (canUserEditAccess(user, id)) {
			if (!accessRepository.findById(id).getUser().getUserId().equals(user.getUserId()))
				accessRepository.deleteById(id);
			else
				throw new GenericOPException(RT_EXCEP);
		} 
		else
			throw new GenericOPException(RT_EXCEP);
	}

	@Override
	public BinaryFileAccess updateBinaryFileAccess(String id, String accesstype, User user) throws GenericOPException {
		if (canUserEditAccess(user, id)) {
			final BinaryFileAccess access = accessRepository.findById(id);
			access.setAccessType(Type.valueOf(accesstype));
			return accessRepository.save(access);
		}

		throw new GenericOPException(RT_EXCEP);
	}

	@Override
	public boolean canUserEditAccess(User user, String id) {
		final BinaryFile file = accessRepository.findById(id).getBinaryFile();
		if (user.getRole().getId().equals(Role.Type.ROLE_ADMINISTRATOR.name()) || file.getOwner().equals(user))
			return true;
		else
			return (accessRepository.findByUserAndWriteAccess(file, user) != null);
	}

}
