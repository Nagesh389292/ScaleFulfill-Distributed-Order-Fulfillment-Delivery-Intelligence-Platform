import React, { useEffect, useRef } from 'react';
import * as THREE from 'three';

export const Network3DCanvas: React.FC<{ height?: number }> = ({ height = 240 }) => {
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    // Scene, Camera, Renderer
    const scene = new THREE.Scene();
    scene.fog = new THREE.FogExp2(0x090d16, 0.035);

    const width = container.clientWidth || 800;
    const camera = new THREE.PerspectiveCamera(45, width / height, 0.1, 100);
    camera.position.set(0, 4, 11);
    camera.lookAt(0, 0, 0);

    const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
    renderer.setSize(width, height);
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    renderer.toneMapping = THREE.ACESFilmicToneMapping;
    renderer.toneMappingExposure = 1.2;
    container.appendChild(renderer.domElement);

    // Lights
    const ambientLight = new THREE.AmbientLight(0xffffff, 0.6);
    scene.add(ambientLight);

    const pointLightCyan = new THREE.PointLight(0x00f0ff, 4, 20);
    pointLightCyan.position.set(-5, 4, 3);
    scene.add(pointLightCyan);

    const pointLightPink = new THREE.PointLight(0xff007a, 4, 20);
    pointLightPink.position.set(5, 3, 2);
    scene.add(pointLightPink);

    const pointLightBlue = new THREE.PointLight(0x3b82f6, 3, 20);
    pointLightBlue.position.set(0, -3, 4);
    scene.add(pointLightBlue);

    // 3D Objects Group (Floating futuristic geometry resembling user screenshot)
    const objectsGroup = new THREE.Group();
    scene.add(objectsGroup);

    // 1. Central Metallic Torus / Ring (Kafka Event Bus)
    const ringGeo = new THREE.TorusGeometry(2.4, 0.12, 16, 100);
    const ringMat = new THREE.MeshStandardMaterial({
      color: 0x38bdf8,
      metalness: 0.85,
      roughness: 0.15,
      emissive: 0x0284c7,
      emissiveIntensity: 0.3,
    });
    const mainRing = new THREE.Mesh(ringGeo, ringMat);
    mainRing.rotation.x = Math.PI / 2.8;
    objectsGroup.add(mainRing);

    // 2. Second Concentric Orbit Ring
    const outerRingGeo = new THREE.TorusGeometry(3.6, 0.05, 16, 100);
    const outerRingMat = new THREE.MeshStandardMaterial({
      color: 0x818cf8,
      metalness: 0.9,
      roughness: 0.2,
      wireframe: true,
    });
    const outerRing = new THREE.Mesh(outerRingGeo, outerRingMat);
    outerRing.rotation.x = Math.PI / 2.5;
    objectsGroup.add(outerRing);

    // 3. Floating Rounded Cubes (Fulfillment Nodes)
    const cubeGeo = new THREE.BoxGeometry(0.7, 0.7, 0.7);
    const cubeMatCyan = new THREE.MeshStandardMaterial({
      color: 0x0ea5e9,
      metalness: 0.9,
      roughness: 0.2,
    });
    const cubeMatPink = new THREE.MeshStandardMaterial({
      color: 0xf43f5e,
      metalness: 0.85,
      roughness: 0.15,
      emissive: 0xe11d48,
      emissiveIntensity: 0.25,
    });
    const cubeMatEmerald = new THREE.MeshStandardMaterial({
      color: 0x10b981,
      metalness: 0.8,
      roughness: 0.2,
    });

    const nodes: THREE.Mesh[] = [];

    // HYD-01 Node
    const node1 = new THREE.Mesh(cubeGeo, cubeMatCyan);
    node1.position.set(-2.8, 0.5, 0.5);
    objectsGroup.add(node1);
    nodes.push(node1);

    // BLR-01 Node
    const node2 = new THREE.Mesh(cubeGeo, cubeMatPink);
    node2.position.set(2.8, -0.2, -0.5);
    objectsGroup.add(node2);
    nodes.push(node2);

    // Order Service / Ingress Node
    const node3 = new THREE.Mesh(cubeGeo, cubeMatEmerald);
    node3.position.set(0, 1.4, -1.8);
    objectsGroup.add(node3);
    nodes.push(node3);

    // 4. Floating Particle Cloud (Events in transit)
    const particleCount = 180;
    const particleGeo = new THREE.BufferGeometry();
    const positions = new Float32Array(particleCount * 3);
    const colors = new Float32Array(particleCount * 3);

    for (let i = 0; i < particleCount; i++) {
      const angle = (i / particleCount) * Math.PI * 2;
      const radius = 2.2 + (Math.random() - 0.5) * 1.5;
      positions[i * 3] = Math.cos(angle) * radius;
      positions[i * 3 + 1] = (Math.random() - 0.5) * 1.2;
      positions[i * 3 + 2] = Math.sin(angle) * radius;

      // Electric Cyan / Violet gradients
      if (i % 2 === 0) {
        colors[i * 3] = 0.22;
        colors[i * 3 + 1] = 0.74;
        colors[i * 3 + 2] = 0.97;
      } else {
        colors[i * 3] = 0.95;
        colors[i * 3 + 1] = 0.25;
        colors[i * 3 + 2] = 0.65;
      }
    }

    particleGeo.setAttribute('position', new THREE.BufferAttribute(positions, 3));
    particleGeo.setAttribute('color', new THREE.BufferAttribute(colors, 3));

    const particleMat = new THREE.PointsMaterial({
      size: 0.08,
      vertexColors: true,
      transparent: true,
      opacity: 0.85,
      blending: THREE.AdditiveBlending,
    });

    const particles = new THREE.Points(particleGeo, particleMat);
    objectsGroup.add(particles);

    // Mouse Interaction
    let mouseX = 0;
    let mouseY = 0;
    let targetX = 0;
    let targetY = 0;

    const handleMouseMove = (event: MouseEvent) => {
      const rect = container.getBoundingClientRect();
      const x = event.clientX - rect.left - rect.width / 2;
      const y = event.clientY - rect.top - rect.height / 2;
      mouseX = (x / rect.width) * 0.8;
      mouseY = (y / rect.height) * 0.8;
    };

    window.addEventListener('mousemove', handleMouseMove);

    // Resize Handler
    const handleResize = () => {
      if (!container) return;
      const newWidth = container.clientWidth;
      camera.aspect = newWidth / height;
      camera.updateProjectionMatrix();
      renderer.setSize(newWidth, height);
    };

    window.addEventListener('resize', handleResize);

    // Animation Loop
    let animId: number;
    let clock = new THREE.Clock();

    const animate = () => {
      animId = requestAnimationFrame(animate);
      const elapsed = clock.getElapsedTime();

      // Smooth camera / group rotation damping
      targetX += (mouseX - targetX) * 0.05;
      targetY += (mouseY - targetY) * 0.05;

      objectsGroup.rotation.y = elapsed * 0.25 + targetX * 0.8;
      objectsGroup.rotation.x = 0.15 + targetY * 0.4;

      // Orbit particles
      particles.rotation.y = -elapsed * 0.4;

      // Animate floating nodes
      nodes.forEach((node, idx) => {
        node.position.y += Math.sin(elapsed * 2 + idx * 1.5) * 0.003;
        node.rotation.x += 0.01;
        node.rotation.y += 0.015;
      });

      // Pulse main ring
      mainRing.rotation.z = elapsed * 0.1;

      renderer.render(scene, camera);
    };

    animate();

    return () => {
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('resize', handleResize);
      cancelAnimationFrame(animId);
      if (renderer.domElement && container.contains(renderer.domElement)) {
        container.removeChild(renderer.domElement);
      }
      renderer.dispose();
      ringGeo.dispose();
      ringMat.dispose();
      outerRingGeo.dispose();
      outerRingMat.dispose();
      cubeGeo.dispose();
      cubeMatCyan.dispose();
      cubeMatPink.dispose();
      cubeMatEmerald.dispose();
      particleGeo.dispose();
      particleMat.dispose();
    };
  }, [height]);

  return (
    <div
      ref={containerRef}
      style={{
        width: '100%',
        height: `${height}px`,
        position: 'relative',
        overflow: 'hidden',
        borderRadius: '16px',
        background: 'radial-gradient(ellipse at 50% 40%, rgba(30, 41, 59, 0.4) 0%, rgba(9, 13, 22, 0.95) 100%)',
        border: '1px solid rgba(255, 255, 255, 0.06)',
      }}
    >
      <div style={{
        position: 'absolute',
        top: '12px',
        left: '16px',
        display: 'flex',
        alignItems: 'center',
        gap: '8px',
        zIndex: 5,
        pointerEvents: 'none',
      }}>
        <span className="badge badge-cyan" style={{ backdropFilter: 'blur(8px)', background: 'rgba(6, 182, 212, 0.2)' }}>
          ● LIVE 3D TOPOLOGY
        </span>
        <span style={{ fontSize: '11px', color: 'var(--text-dim)', fontFamily: 'var(--font-mono)' }}>
          INTERACTIVE MESH • 3 FC HUBS • KAFKA EVENT BUS
        </span>
      </div>
    </div>
  );
};
